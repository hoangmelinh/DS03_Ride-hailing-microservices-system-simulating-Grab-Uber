package com.goride.trip.application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.common.exception.InvalidRequestException;
import com.goride.trip.application.dto.event.TripAcceptedPayload;
import com.goride.trip.application.dto.event.TripCancelledPayload;
import com.goride.trip.application.dto.event.TripCompletedPayload;
import com.goride.trip.application.dto.event.TripCreatedPayload;
import com.goride.trip.application.dto.event.TripDriverAssignedPayload;
import com.goride.trip.application.dto.event.TripStartedPayload;
import com.goride.trip.application.dto.request.AssignDriverRequest;
import com.goride.trip.application.dto.request.CancelTripRequest;
import com.goride.trip.application.dto.request.CreateTripRequest;
import com.goride.trip.application.dto.request.EstimateFareRequest;
import com.goride.trip.application.dto.response.FareEstimateResponse;
import com.goride.trip.application.dto.response.TripDetailResponse;
import com.goride.trip.application.dto.response.TripResponse;
import com.goride.trip.application.dto.response.TripStatusHistoryResponse;
import com.goride.trip.application.service.TripService;
import com.goride.trip.domain.model.ActorType;
import com.goride.trip.domain.model.IdempotencyStatus;
import com.goride.trip.domain.model.TripStatus;
import com.goride.trip.infrastructure.persistence.entity.IdempotencyRecordEntity;
import com.goride.trip.infrastructure.persistence.entity.OutboxEventEntity;
import com.goride.trip.infrastructure.persistence.entity.TripEntity;
import com.goride.trip.infrastructure.persistence.entity.TripStatusHistoryEntity;
import com.goride.trip.infrastructure.persistence.repository.IdempotencyRecordRepository;
import com.goride.trip.infrastructure.persistence.repository.OutboxEventRepository;
import com.goride.trip.infrastructure.persistence.repository.TripRepository;
import com.goride.trip.infrastructure.persistence.repository.TripStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TripServiceImpl implements TripService {

    private static final Logger log = LoggerFactory.getLogger(TripServiceImpl.class);

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final BigDecimal BASE_FARE = new BigDecimal("15000.00");
    private static final double BASE_DISTANCE_KM = 2.0;
    private static final BigDecimal RATE_PER_KM = new BigDecimal("10000.00");

    private final TripRepository tripRepository;
    private final TripStatusHistoryRepository tripStatusHistoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    public TripServiceImpl(TripRepository tripRepository,
                           TripStatusHistoryRepository tripStatusHistoryRepository,
                           OutboxEventRepository outboxEventRepository,
                           IdempotencyRecordRepository idempotencyRecordRepository,
                           ObjectMapper objectMapper) {
        this.tripRepository = tripRepository;
        this.tripStatusHistoryRepository = tripStatusHistoryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public TripResponse createTrip(CreateTripRequest request, UUID correlationId) {
        if (request.getPassengerId() == null) {
            throw new InvalidRequestException("Passenger ID is required");
        }

        UUID effectiveCorrelationId = correlationId != null ? correlationId : UUID.randomUUID();

        // 1. Calculate fare if not already provided
        BigDecimal estimatedFare = request.getEstimatedFare();
        if (estimatedFare == null || estimatedFare.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal distanceKm = calculateHaversineDistance(
                    request.getPickupLatitude(), request.getPickupLongitude(),
                    request.getDestinationLatitude(), request.getDestinationLongitude()
            );
            estimatedFare = calculateFare(distanceKm);
        }

        // 2. Persist TripEntity in REQUESTED state
        TripEntity trip = new TripEntity(
                request.getPassengerId(),
                request.getPickupLatitude(),
                request.getPickupLongitude(),
                request.getPickupAddress(),
                request.getDestinationLatitude(),
                request.getDestinationLongitude(),
                request.getDestinationAddress(),
                estimatedFare
        );
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            trip.setCurrency(request.getCurrency());
        }
        trip = tripRepository.save(trip);

        // 3. Persist initial status history
        TripStatusHistoryEntity history = new TripStatusHistoryEntity(
                trip.getId(),
                null,
                TripStatus.REQUESTED.name(),
                "Trip requested by passenger",
                ActorType.USER,
                trip.getPassengerId(),
                effectiveCorrelationId
        );
        tripStatusHistoryRepository.save(history);

        // 4. Persist Outbox Event within the same transaction (Transactional Outbox Pattern)
        TripCreatedPayload payload = new TripCreatedPayload(
                trip.getId(),
                trip.getPassengerId(),
                trip.getPickupLatitude(),
                trip.getPickupLongitude(),
                trip.getPickupAddress(),
                trip.getDestinationLatitude(),
                trip.getDestinationLongitude(),
                trip.getDestinationAddress(),
                trip.getEstimatedFare(),
                trip.getCurrency(),
                trip.getStatus().name(),
                trip.getRequestedAt()
        );
        saveOutboxEvent(trip.getId(), "trip.created", payload, effectiveCorrelationId);

        log.info("Created trip [id={}, passengerId={}, fare={}, correlationId={}] and queued outbox event 'trip.created'",
                trip.getId(), trip.getPassengerId(), trip.getEstimatedFare(), effectiveCorrelationId);

        return TripResponse.fromEntity(trip);
    }

    @Override
    @Transactional
    public TripResponse assignDriver(UUID tripId, AssignDriverRequest request, String idempotencyKey, UUID correlationId) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecordEntity> existing = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                IdempotencyRecordEntity record = existing.get();
                if (record.getStatus() == IdempotencyStatus.COMPLETED && record.getResponseBody() != null) {
                    try {
                        return objectMapper.readValue(record.getResponseBody(), TripResponse.class);
                    } catch (Exception e) {
                        log.warn("Failed to parse cached idempotency response: {}", e.getMessage());
                    }
                } else if (record.getStatus() == IdempotencyStatus.PROCESSING) {
                    throw new InvalidRequestException("Concurrent request in progress for idempotency key: " + idempotencyKey);
                }
            }
        }

        TripEntity trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip", tripId));

        if (trip.getStatus() == TripStatus.DRIVER_ASSIGNED && request.getDriverId().equals(trip.getDriverId())) {
            return TripResponse.fromEntity(trip);
        }

        if (trip.getStatus() != TripStatus.REQUESTED && trip.getStatus() != TripStatus.MATCHING) {
            throw new InvalidRequestException("Cannot assign driver to trip with status: " + trip.getStatus());
        }

        UUID effectiveCorrelationId = correlationId != null ? correlationId : UUID.randomUUID();
        String fromStatus = trip.getStatus().name();

        trip.setDriverId(request.getDriverId());
        trip.setStatus(TripStatus.DRIVER_ASSIGNED);
        trip.setAssignedAt(Instant.now());
        trip = tripRepository.save(trip);

        TripStatusHistoryEntity history = new TripStatusHistoryEntity(
                trip.getId(),
                fromStatus,
                TripStatus.DRIVER_ASSIGNED.name(),
                "Driver assigned by matching saga",
                ActorType.SYSTEM,
                request.getDriverId(),
                effectiveCorrelationId
        );
        tripStatusHistoryRepository.save(history);

        TripDriverAssignedPayload payload = new TripDriverAssignedPayload(
                trip.getId(),
                trip.getPassengerId(),
                trip.getDriverId(),
                trip.getStatus().name(),
                trip.getAssignedAt()
        );
        saveOutboxEvent(trip.getId(), "trip.driver-assigned", payload, effectiveCorrelationId);

        TripResponse response = TripResponse.fromEntity(trip);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                String respJson = objectMapper.writeValueAsString(response);
                IdempotencyRecordEntity record = new IdempotencyRecordEntity(
                        idempotencyKey,
                        "ASSIGN_DRIVER",
                        tripId.toString(),
                        IdempotencyStatus.COMPLETED,
                        Instant.now().plus(Duration.ofHours(24))
                );
                record.setResponseStatus(200);
                record.setResponseBody(respJson);
                idempotencyRecordRepository.save(record);
            } catch (Exception e) {
                log.warn("Failed to save idempotency record: {}", e.getMessage());
            }
        }

        log.info("Assigned driver [{}] to trip [{}] with correlationId [{}]",
                request.getDriverId(), trip.getId(), effectiveCorrelationId);

        return response;
    }

    @Override
    @Transactional
    public TripResponse acceptTrip(UUID tripId, UUID driverId, UUID correlationId) {
        TripEntity trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip", tripId));

        if (trip.getStatus() == TripStatus.ACCEPTED) {
            return TripResponse.fromEntity(trip);
        }

        if (trip.getStatus() != TripStatus.DRIVER_ASSIGNED) {
            throw new InvalidRequestException("Cannot accept trip with status: " + trip.getStatus());
        }

        if (driverId != null && trip.getDriverId() != null && !driverId.equals(trip.getDriverId())) {
            throw new InvalidRequestException("Driver mismatch: only the assigned driver can accept this trip");
        }

        UUID effectiveCorrelationId = correlationId != null ? correlationId : UUID.randomUUID();
        String fromStatus = trip.getStatus().name();

        trip.setStatus(TripStatus.ACCEPTED);
        trip.setAcceptedAt(Instant.now());
        trip = tripRepository.save(trip);

        TripStatusHistoryEntity history = new TripStatusHistoryEntity(
                trip.getId(),
                fromStatus,
                TripStatus.ACCEPTED.name(),
                "Trip accepted by driver",
                ActorType.DRIVER,
                trip.getDriverId(),
                effectiveCorrelationId
        );
        tripStatusHistoryRepository.save(history);

        TripAcceptedPayload payload = new TripAcceptedPayload(
                trip.getId(),
                trip.getPassengerId(),
                trip.getDriverId(),
                trip.getStatus().name(),
                trip.getAcceptedAt()
        );
        saveOutboxEvent(trip.getId(), "trip.accepted", payload, effectiveCorrelationId);

        log.info("Trip [{}] accepted by driver [{}]", trip.getId(), trip.getDriverId());
        return TripResponse.fromEntity(trip);
    }

    @Override
    @Transactional
    public TripResponse startTrip(UUID tripId, UUID driverId, UUID correlationId) {
        TripEntity trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip", tripId));

        if (trip.getStatus() == TripStatus.IN_PROGRESS) {
            return TripResponse.fromEntity(trip);
        }

        if (trip.getStatus() != TripStatus.ACCEPTED && trip.getStatus() != TripStatus.DRIVER_ASSIGNED) {
            throw new InvalidRequestException("Cannot start trip with status: " + trip.getStatus());
        }

        if (driverId != null && trip.getDriverId() != null && !driverId.equals(trip.getDriverId())) {
            throw new InvalidRequestException("Driver mismatch: only the assigned driver can start this trip");
        }

        UUID effectiveCorrelationId = correlationId != null ? correlationId : UUID.randomUUID();
        String fromStatus = trip.getStatus().name();

        trip.setStatus(TripStatus.IN_PROGRESS);
        trip.setStartedAt(Instant.now());
        trip = tripRepository.save(trip);

        TripStatusHistoryEntity history = new TripStatusHistoryEntity(
                trip.getId(),
                fromStatus,
                TripStatus.IN_PROGRESS.name(),
                "Trip started by driver",
                ActorType.DRIVER,
                trip.getDriverId(),
                effectiveCorrelationId
        );
        tripStatusHistoryRepository.save(history);

        TripStartedPayload payload = new TripStartedPayload(
                trip.getId(),
                trip.getPassengerId(),
                trip.getDriverId(),
                trip.getStatus().name(),
                trip.getStartedAt()
        );
        saveOutboxEvent(trip.getId(), "trip.started", payload, effectiveCorrelationId);

        log.info("Trip [{}] started by driver [{}]", trip.getId(), trip.getDriverId());
        return TripResponse.fromEntity(trip);
    }

    @Override
    @Transactional
    public TripResponse completeTrip(UUID tripId, UUID driverId, UUID correlationId) {
        TripEntity trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip", tripId));

        if (trip.getStatus() == TripStatus.COMPLETED) {
            return TripResponse.fromEntity(trip);
        }

        if (trip.getStatus() != TripStatus.IN_PROGRESS) {
            throw new InvalidRequestException("Trip must be IN_PROGRESS to complete. Current status: " + trip.getStatus());
        }

        if (driverId != null && trip.getDriverId() != null && !driverId.equals(trip.getDriverId())) {
            throw new InvalidRequestException("Driver mismatch: only the assigned driver can complete this trip");
        }

        UUID effectiveCorrelationId = correlationId != null ? correlationId : UUID.randomUUID();
        String fromStatus = trip.getStatus().name();

        trip.setStatus(TripStatus.COMPLETED);
        trip.setCompletedAt(Instant.now());
        if (trip.getFinalFare() == null) {
            trip.setFinalFare(trip.getEstimatedFare());
        }
        trip = tripRepository.save(trip);

        TripStatusHistoryEntity history = new TripStatusHistoryEntity(
                trip.getId(),
                fromStatus,
                TripStatus.COMPLETED.name(),
                "Trip completed by driver",
                ActorType.DRIVER,
                trip.getDriverId(),
                effectiveCorrelationId
        );
        tripStatusHistoryRepository.save(history);

        // Publish trip.completed outbox event (consumed by Payment Service and Notification Service)
        TripCompletedPayload payload = new TripCompletedPayload(
                trip.getId(),
                trip.getPassengerId(),
                trip.getDriverId(),
                trip.getFinalFare(),
                trip.getCurrency(),
                trip.getStatus().name(),
                trip.getCompletedAt()
        );
        saveOutboxEvent(trip.getId(), "trip.completed", payload, effectiveCorrelationId);

        log.info("Trip [{}] completed. Final fare: {} {}, queued 'trip.completed' outbox event",
                trip.getId(), trip.getFinalFare(), trip.getCurrency());
        return TripResponse.fromEntity(trip);
    }

    @Override
    @Transactional
    public TripResponse cancelTrip(UUID tripId, CancelTripRequest request, UUID actorId, ActorType actorType, UUID correlationId) {
        TripEntity trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip", tripId));

        if (trip.getStatus() == TripStatus.CANCELLED) {
            return TripResponse.fromEntity(trip);
        }

        if (trip.getStatus() == TripStatus.COMPLETED) {
            throw new InvalidRequestException("Cannot cancel a trip that is already COMPLETED");
        }

        UUID effectiveCorrelationId = correlationId != null ? correlationId : UUID.randomUUID();
        String fromStatus = trip.getStatus().name();
        String reason = (request != null && request.getReason() != null && !request.getReason().isBlank())
                ? request.getReason()
                : "Trip cancelled by " + (actorType != null ? actorType : ActorType.USER);

        trip.setStatus(TripStatus.CANCELLED);
        trip.setCancelledAt(Instant.now());
        trip.setCancellationReason(reason);
        trip = tripRepository.save(trip);

        TripStatusHistoryEntity history = new TripStatusHistoryEntity(
                trip.getId(),
                fromStatus,
                TripStatus.CANCELLED.name(),
                reason,
                actorType != null ? actorType : ActorType.USER,
                actorId,
                effectiveCorrelationId
        );
        tripStatusHistoryRepository.save(history);

        TripCancelledPayload payload = new TripCancelledPayload(
                trip.getId(),
                trip.getPassengerId(),
                trip.getDriverId(),
                reason,
                trip.getStatus().name(),
                trip.getCancelledAt()
        );
        saveOutboxEvent(trip.getId(), "trip.cancelled", payload, effectiveCorrelationId);

        log.info("Trip [{}] cancelled by [{}] (reason: {})", trip.getId(), actorType, reason);
        return TripResponse.fromEntity(trip);
    }

    @Override
    @Transactional(readOnly = true)
    public TripResponse getTripById(UUID tripId) {
        TripEntity trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip", tripId));
        return TripResponse.fromEntity(trip);
    }

    @Override
    @Transactional(readOnly = true)
    public TripDetailResponse getTripDetail(UUID tripId) {
        TripEntity trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip", tripId));

        List<TripStatusHistoryEntity> historyList = tripStatusHistoryRepository.findByTripIdOrderByChangedAtAsc(tripId);
        List<TripStatusHistoryResponse> historyResponses = historyList.stream()
                .map(TripStatusHistoryResponse::fromEntity)
                .toList();

        return new TripDetailResponse(TripResponse.fromEntity(trip), historyResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TripResponse> getTripsByPassenger(UUID passengerId, Pageable pageable) {
        return tripRepository.findByPassengerIdOrderByRequestedAtDesc(passengerId, pageable)
                .map(TripResponse::fromEntity);
    }

    @Override
    public FareEstimateResponse estimateFare(EstimateFareRequest request) {
        BigDecimal distanceKm = calculateHaversineDistance(
                request.getPickupLatitude(), request.getPickupLongitude(),
                request.getDestinationLatitude(), request.getDestinationLongitude()
        );
        BigDecimal fare = calculateFare(distanceKm);
        return new FareEstimateResponse(distanceKm, fare, "VND", BASE_FARE, RATE_PER_KM);
    }

    private void saveOutboxEvent(UUID aggregateId, String eventType, Object payload, UUID correlationId) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            OutboxEventEntity outboxEvent = new OutboxEventEntity(
                    UUID.randomUUID(),
                    "TRIP",
                    aggregateId,
                    eventType,
                    jsonPayload,
                    correlationId,
                    null,
                    Instant.now()
            );
            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload for tripId: {}", aggregateId, e);
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }
    }

    private BigDecimal calculateHaversineDistance(BigDecimal pickupLat, BigDecimal pickupLon,
                                                  BigDecimal destLat, BigDecimal destLon) {
        double lat1 = Math.toRadians(pickupLat.doubleValue());
        double lon1 = Math.toRadians(pickupLon.doubleValue());
        double lat2 = Math.toRadians(destLat.doubleValue());
        double lon2 = Math.toRadians(destLon.doubleValue());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS_KM * c;

        return BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateFare(BigDecimal distanceKm) {
        double dist = distanceKm.doubleValue();
        if (dist <= BASE_DISTANCE_KM) {
            return BASE_FARE;
        }
        double extraKm = dist - BASE_DISTANCE_KM;
        BigDecimal extraFare = RATE_PER_KM.multiply(BigDecimal.valueOf(extraKm));
        return BASE_FARE.add(extraFare).setScale(2, RoundingMode.HALF_UP);
    }
}
