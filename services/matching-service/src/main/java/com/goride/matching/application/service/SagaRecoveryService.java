package com.goride.matching.application.service;

import com.goride.matching.application.port.out.TripServiceClientPort.AssignCallStatus;
import com.goride.matching.application.port.out.TripServiceClientPort.AssignTripOutcome;
import com.goride.matching.domain.model.MatchingSaga;
import com.goride.matching.domain.model.SagaStatus;
import com.goride.matching.domain.repository.MatchingSagaRepository;
import com.goride.matching.domain.repository.OutboxEventRepository;
import com.goride.matching.domain.model.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "goride.matching.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class SagaRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(SagaRecoveryService.class);

    private final MatchingSagaRepository sagaRepository;
    private final MatchingSagaOrchestrator orchestrator;
    private final OutboxEventRepository outboxEventRepository;
    private final int maxCompensationAttempts;
    private final int maxReconcileAttempts;

    public SagaRecoveryService(
            MatchingSagaRepository sagaRepository,
            MatchingSagaOrchestrator orchestrator,
            OutboxEventRepository outboxEventRepository,
            @Value("${goride.matching.recovery.max-compensation-attempts:5}") int maxCompensationAttempts,
            @Value("${goride.matching.recovery.max-reconcile-attempts:3}") int maxReconcileAttempts) {
        this.sagaRepository = sagaRepository;
        this.orchestrator = orchestrator;
        this.outboxEventRepository = outboxEventRepository;
        this.maxCompensationAttempts = maxCompensationAttempts;
        this.maxReconcileAttempts = maxReconcileAttempts;
    }

    @Scheduled(fixedDelayString = "${goride.matching.recovery.fixed-delay-ms:5000}")
    public void recoverStuckSagas() {
        recoverPendingCompensations();
        reconcilePendingAssignments();
    }

    public void recoverPendingCompensations() {
        List<MatchingSaga> compensating = sagaRepository.findByStatus(SagaStatus.COMPENSATING);
        for (MatchingSaga saga : compensating) {
            try {
                if (saga.getAttemptCount() >= maxCompensationAttempts + 10) {
                    log.error("Saga {} exceeded max compensation attempts; marking FAILED for manual intervention", saga.getId());
                    saga.markFailed("COMPENSATION_EXHAUSTED", "Exceeded max compensation retries");
                    sagaRepository.save(saga);
                    outboxEventRepository.save(OutboxEvent.create(
                            "MATCHING_SAGA", saga.getId(), "matching.failed",
                            "{\"sagaId\":\"" + saga.getId() + "\",\"tripId\":\"" + saga.getTripId() + "\",\"reasonCode\":\"COMPENSATION_EXHAUSTED\"}",
                            saga.getCorrelationId(), null));
                    continue;
                }
                saga.incrementAttempt();
                saga = sagaRepository.save(saga);
                orchestrator.executeCompensation(saga, null);
            } catch (Exception ex) {
                log.warn("Error recovering COMPENSATING saga {}: {}", saga.getId(), ex.getMessage());
            }
        }
    }

    public void reconcilePendingAssignments() {
        List<MatchingSaga> pendingAssign = sagaRepository.findByStatusAndCurrentStep(
                SagaStatus.DRIVER_RESERVED, "ASSIGN_TRIP_RECONCILE_PENDING");
        for (MatchingSaga saga : pendingAssign) {
            try {
                if (saga.getAttemptCount() >= maxReconcileAttempts) {
                    log.error("Saga {} exceeded max reconcile attempts ({}); triggering compensation",
                            saga.getId(), maxReconcileAttempts);
                    saga.markCompensating("RECONCILE_EXHAUSTED", "Exceeded max assign trip reconcile attempts");
                    saga = sagaRepository.save(saga);
                    orchestrator.executeCompensation(saga, null);
                    continue;
                }

                saga.incrementAttempt();
                saga = sagaRepository.save(saga);

                String assignKey = MatchingSagaOrchestrator.buildAssignIdempotencyKey(
                        saga.getId(), saga.getSelectedDriverId());
                AssignTripOutcome outcome = orchestrator.assignWithTimeoutReconcile(
                        saga.getTripId(), saga.getSelectedDriverId(), assignKey, saga.getCorrelationId());

                if (outcome.isSuccess()) {
                    saga.markTripAssigned();
                    saga = sagaRepository.save(saga);
                    saga.markCompleted();
                    sagaRepository.save(saga);
                    outboxEventRepository.save(OutboxEvent.create(
                            "MATCHING_SAGA", saga.getId(), "matching.completed",
                            "{\"sagaId\":\"" + saga.getId() + "\",\"tripId\":\"" + saga.getTripId() + "\",\"driverId\":\"" + saga.getSelectedDriverId() + "\",\"status\":\"COMPLETED\"}",
                            saga.getCorrelationId(), null));
                } else if (outcome.status() != AssignCallStatus.TIMEOUT) {
                    saga.markCompensating(outcome.errorCode(), outcome.errorMessage());
                    saga = sagaRepository.save(saga);
                    orchestrator.executeCompensation(saga, null);
                }
            } catch (Exception ex) {
                log.warn("Error reconciling pending assignment for saga {}: {}", saga.getId(), ex.getMessage());
            }
        }
    }
}
