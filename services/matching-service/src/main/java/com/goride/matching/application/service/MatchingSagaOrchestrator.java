package com.goride.matching.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.matching.application.port.in.ProcessTripCreatedCommand;
import com.goride.matching.application.port.in.ProcessTripCreatedUseCase;
import com.goride.matching.application.port.out.CandidateDriverDiscoveryPort;
import com.goride.matching.application.port.out.CandidateDriverDiscoveryPort.RankedDriverCandidate;
import com.goride.matching.application.port.out.DriverServiceClientPort;
import com.goride.matching.application.port.out.DriverServiceClientPort.ReleaseOutcome;
import com.goride.matching.application.port.out.DriverServiceClientPort.ReserveCallStatus;
import com.goride.matching.application.port.out.DriverServiceClientPort.ReserveOutcome;
import com.goride.matching.application.port.out.TripServiceClientPort;
import com.goride.matching.application.port.out.TripServiceClientPort.AssignCallStatus;
import com.goride.matching.application.port.out.TripServiceClientPort.AssignTripOutcome;
import com.goride.matching.domain.model.MatchingAttempt;
import com.goride.matching.domain.model.MatchingSaga;
import com.goride.matching.domain.model.OutboxEvent;
import com.goride.matching.domain.model.ReserveResult;
import com.goride.matching.domain.repository.MatchingAttemptRepository;
import com.goride.matching.domain.repository.MatchingSagaRepository;
import com.goride.matching.domain.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class MatchingSagaOrchestrator implements ProcessTripCreatedUseCase {

    private static final Logger log = LoggerFactory.getLogger(MatchingSagaOrchestrator.class);

    private final MatchingSagaRepository sagaRepository;
    private final MatchingAttemptRepository attemptRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CandidateDriverDiscoveryPort candidateDiscoveryPort;
    private final DriverServiceClientPort driverServiceClientPort;
    private final TripServiceClientPort tripServiceClientPort;
    private final ObjectMapper objectMapper;
    private final int reserveTtlSeconds;
    private final int reserveTimeoutRetries;
    private final int assignTimeoutRetries;

    public MatchingSagaOrchestrator(
            MatchingSagaRepository sagaRepository,
            MatchingAttemptRepository attemptRepository,
            OutboxEventRepository outboxEventRepository,
            CandidateDriverDiscoveryPort candidateDiscoveryPort,
            DriverServiceClientPort driverServiceClientPort,
            TripServiceClientPort tripServiceClientPort,
            ObjectMapper objectMapper,
            @Value("${goride.matching.reserve-ttl-seconds:30}") int reserveTtlSeconds,
            @Value("${goride.matching.reserve-timeout-retries:2}") int reserveTimeoutRetries,
            @Value("${goride.matching.assign-timeout-retries:2}") int assignTimeoutRetries) {
        this.sagaRepository = sagaRepository;
        this.attemptRepository = attemptRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.candidateDiscoveryPort = candidateDiscoveryPort;
        this.driverServiceClientPort = driverServiceClientPort;
        this.tripServiceClientPort = tripServiceClientPort;
        this.objectMapper = objectMapper;
        this.reserveTtlSeconds = reserveTtlSeconds;
        this.reserveTimeoutRetries = reserveTimeoutRetries;
        this.assignTimeoutRetries = assignTimeoutRetries;
    }

    @Override
    public MatchingSaga processTripCreated(ProcessTripCreatedCommand command) {
        Optional<MatchingSaga> existingSagaOpt = sagaRepository.findByTripId(command.tripId());
        if (existingSagaOpt.isPresent()) {
            MatchingSaga existing = existingSagaOpt.get();
            log.info("Saga already exists for tripId={} (sagaId={}, status={}), skipping duplicate start",
                    command.tripId(), existing.getId(), existing.getStatus());
            return existing;
        }

        MatchingSaga saga = MatchingSaga.start(command.tripId(), command.correlationId());
        saga = sagaRepository.save(saga);
        log.info("Started MatchingSaga id={} for tripId={}, correlationId={}",
                saga.getId(), command.tripId(), command.correlationId());

        // Step 1: Discover Candidates
        List<RankedDriverCandidate> candidates = candidateDiscoveryPort.findCandidates(
                command.pickupLatitude(), command.pickupLongitude());

        if (candidates.isEmpty()) {
            log.warn("No candidate drivers found for tripId={}, sagaId={}", command.tripId(), saga.getId());
            saga.markFailed("NO_CANDIDATE_FOUND", "No available driver candidates found");
            saga = sagaRepository.save(saga);
            saveFailedOutbox(saga, "NO_CANDIDATE_FOUND", command.eventId());
            return saga;
        }

        saga.markCandidateSelected();
        saga = sagaRepository.save(saga);

        // Step 2: Candidate Fallback Loop (Reserve Driver)
        UUID reservedDriverId = null;
        for (RankedDriverCandidate candidate : candidates) {
            saga.incrementAttempt();
            int attemptNo = saga.getAttemptCount();
            String reserveIdempKey = buildReserveIdempotencyKey(saga.getId(), candidate.driverId());

            ReserveOutcome outcome = reserveWithTimeoutReconcile(
                    candidate.driverId(), command.tripId(), reserveIdempKey, command.correlationId());

            ReserveResult attemptResult = mapReserveResult(outcome.status());
            MatchingAttempt attempt = MatchingAttempt.record(
                    saga.getId(),
                    candidate.driverId(),
                    candidate.rank(),
                    candidate.distanceMeters(),
                    attemptNo,
                    attemptResult,
                    outcome.errorCode(),
                    outcome.errorMessage()
            );
            attemptRepository.save(attempt);

            if (outcome.isSuccess()) {
                saga.markDriverReserved(candidate.driverId());
                saga = sagaRepository.save(saga);
                reservedDriverId = candidate.driverId();
                log.info("Saga {} reserved driver {} (rank={}, attempt={})",
                        saga.getId(), reservedDriverId, candidate.rank(), attemptNo);
                break;
            }

            log.warn("Candidate {} failed for saga {} with status={} ({}), falling back if more candidates exist",
                    candidate.driverId(), saga.getId(), outcome.status(), outcome.errorCode());
        }

        if (reservedDriverId == null) {
            saga.markFailed("ALL_CANDIDATES_FAILED", "All candidate drivers failed to reserve");
            saga = sagaRepository.save(saga);
            saveFailedOutbox(saga, "ALL_CANDIDATES_FAILED", command.eventId());
            return saga;
        }

        // Step 3: Assign Driver via Trip Service
        String assignIdempKey = buildAssignIdempotencyKey(saga.getId(), reservedDriverId);
        AssignTripOutcome assignOutcome = assignWithTimeoutReconcile(
                command.tripId(), reservedDriverId, assignIdempKey, command.correlationId());

        if (assignOutcome.isSuccess()) {
            saga.markTripAssigned();
            saga = sagaRepository.save(saga);

            saga.markCompleted();
            saga = sagaRepository.save(saga);
            saveCompletedOutbox(saga, command.eventId());
            log.info("MatchingSaga {} COMPLETED for tripId={}, driverId={}",
                    saga.getId(), command.tripId(), reservedDriverId);
            return saga;
        }

        if (assignOutcome.status() == AssignCallStatus.TIMEOUT) {
            // Indeterminate outcome: DO NOT release driver blindly! Defer to Recovery Worker to reconcile idempotently.
            log.warn("Assign Trip timed out for saga={}, trip={}, driver={}. Deferring reconciliation without releasing.",
                    saga.getId(), command.tripId(), reservedDriverId);
            saga.markAssignReconcilePending("ASSIGN_TIMEOUT", assignOutcome.errorMessage());
            return sagaRepository.save(saga);
        }

        // Definite failure on Assign Trip -> Trigger Saga Compensation
        log.warn("Assign Trip failed definitively for saga {} ({}). Entering COMPENSATING state.",
                saga.getId(), assignOutcome.errorCode());
        saga.markCompensating(assignOutcome.errorCode(), assignOutcome.errorMessage());
        saga = sagaRepository.save(saga);

        return executeCompensation(saga, command.eventId());
    }

    public MatchingSaga executeCompensation(MatchingSaga saga, UUID causationId) {
        UUID driverId = saga.getSelectedDriverId();
        String releaseIdempKey = buildReleaseIdempotencyKey(saga.getId(), driverId);

        ReleaseOutcome releaseOutcome = driverServiceClientPort.releaseDriver(
                driverId, saga.getTripId(), releaseIdempKey, saga.getCorrelationId(), "ASSIGN_FAILED");

        if (releaseOutcome.isSuccess()) {
            saga.markCompensated();
            saga = sagaRepository.save(saga);
            saveFailedOutbox(saga, saga.getLastErrorCode() != null ? saga.getLastErrorCode() : "ASSIGN_FAILED", causationId);
            log.info("Saga {} COMPENSATED: driver {} released from trip {}",
                    saga.getId(), driverId, saga.getTripId());
            return saga;
        }

        // Keep COMPENSATING status so Recovery Worker retries safely
        log.error("Release driver failed for saga {} (driver={}): {}. Keeping status=COMPENSATING for recovery.",
                saga.getId(), driverId, releaseOutcome.errorMessage());
        saga.recordTransientError(releaseOutcome.errorCode(), releaseOutcome.errorMessage());
        return sagaRepository.save(saga);
    }

    public AssignTripOutcome assignWithTimeoutReconcile(UUID tripId, UUID driverId,
                                                        String idempotencyKey, UUID correlationId) {
        AssignTripOutcome outcome = tripServiceClientPort.assignDriver(tripId, driverId, idempotencyKey, correlationId);
        int retries = 0;
        while (outcome.status() == AssignCallStatus.TIMEOUT && retries < assignTimeoutRetries) {
            retries++;
            log.warn("Assign trip timeout for trip={}, driver={}. Reconciling attempt {}/{} with same Idempotency-Key={}",
                    tripId, driverId, retries, assignTimeoutRetries, idempotencyKey);
            outcome = tripServiceClientPort.assignDriver(tripId, driverId, idempotencyKey, correlationId);
        }
        return outcome;
    }

    private ReserveOutcome reserveWithTimeoutReconcile(UUID driverId, UUID tripId,
                                                       String idempotencyKey, UUID correlationId) {
        ReserveOutcome outcome = driverServiceClientPort.reserveDriver(
                driverId, tripId, idempotencyKey, correlationId, reserveTtlSeconds);
        int retries = 0;
        while (outcome.status() == ReserveCallStatus.TIMEOUT && retries < reserveTimeoutRetries) {
            retries++;
            log.warn("Reserve driver timeout for driver={}, trip={}. Reconciling attempt {}/{} with same Idempotency-Key={}",
                    driverId, tripId, retries, reserveTimeoutRetries, idempotencyKey);
            outcome = driverServiceClientPort.reserveDriver(
                    driverId, tripId, idempotencyKey, correlationId, reserveTtlSeconds);
        }
        return outcome;
    }

    private ReserveResult mapReserveResult(ReserveCallStatus status) {
        return switch (status) {
            case SUCCESS -> ReserveResult.SUCCESS;
            case CONFLICT_BUSY -> ReserveResult.DRIVER_BUSY;
            case TIMEOUT -> ReserveResult.TIMEOUT;
            case CLIENT_ERROR, SERVER_ERROR -> ReserveResult.ERROR;
        };
    }

    private void saveCompletedOutbox(MatchingSaga saga, UUID causationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sagaId", saga.getId().toString());
        payload.put("tripId", saga.getTripId().toString());
        payload.put("driverId", saga.getSelectedDriverId().toString());
        payload.put("status", saga.getStatus().name());
        payload.put("completedAt", saga.getCompletedAt().toString());
        outboxEventRepository.save(OutboxEvent.create(
                "MATCHING_SAGA", saga.getId(), "matching.completed",
                serialize(payload), saga.getCorrelationId(), causationId));
    }

    private void saveFailedOutbox(MatchingSaga saga, String reasonCode, UUID causationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sagaId", saga.getId().toString());
        payload.put("tripId", saga.getTripId().toString());
        payload.put("status", saga.getStatus().name());
        payload.put("reasonCode", reasonCode);
        outboxEventRepository.save(OutboxEvent.create(
                "MATCHING_SAGA", saga.getId(), "matching.failed",
                serialize(payload), saga.getCorrelationId(), causationId));
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize matching outbox payload", e);
        }
    }

    public static String buildReserveIdempotencyKey(UUID sagaId, UUID driverId) {
        return "reserve-" + sagaId + "-" + driverId;
    }

    public static String buildAssignIdempotencyKey(UUID sagaId, UUID driverId) {
        return "assign-" + sagaId + "-" + driverId;
    }

    public static String buildReleaseIdempotencyKey(UUID sagaId, UUID driverId) {
        return "release-" + sagaId + "-" + driverId;
    }
}
