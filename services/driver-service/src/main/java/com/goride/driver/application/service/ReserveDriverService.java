package com.goride.driver.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.exception.DuplicateResourceException;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.driver.application.exception.DriverNotAvailableException;
import com.goride.driver.application.port.in.ReserveDriverCommand;
import com.goride.driver.application.port.in.ReserveDriverResult;
import com.goride.driver.application.port.in.ReserveDriverUseCase;
import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.DriverReservation;
import com.goride.driver.domain.model.DriverStatus;
import com.goride.driver.domain.model.OutboxEvent;
import com.goride.driver.domain.repository.DriverRepository;
import com.goride.driver.domain.repository.DriverReservationRepository;
import com.goride.driver.domain.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ReserveDriverService implements ReserveDriverUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReserveDriverService.class);

    private final DriverRepository driverRepository;
    private final DriverReservationRepository driverReservationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public ReserveDriverService(DriverRepository driverRepository,
                                DriverReservationRepository driverReservationRepository,
                                OutboxEventRepository outboxEventRepository,
                                ObjectMapper objectMapper) {
        this.driverRepository = driverRepository;
        this.driverReservationRepository = driverReservationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ReserveDriverResult reserveDriver(ReserveDriverCommand command) {
        log.info("Processing reservation request: driverId={}, tripId={}, idempotencyKey={}, correlationId={}",
                command.driverId(), command.tripId(), command.idempotencyKey(), command.correlationId());

        // 1. Idempotency Check
        Optional<DriverReservation> existingOpt = driverReservationRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existingOpt.isPresent()) {
            DriverReservation existing = existingOpt.get();
            if (existing.getDriverId().equals(command.driverId()) && existing.getTripId().equals(command.tripId())) {
                log.info("Idempotent replay detected for key {}. Returning existing reservation {}",
                        command.idempotencyKey(), existing.getId());
                return new ReserveDriverResult(
                        existing.getId(),
                        existing.getDriverId(),
                        existing.getTripId(),
                        existing.getStatus(),
                        existing.getExpiresAt()
                );
            } else {
                log.warn("Idempotency key conflict: key {} used with different payload (existing driver={}, trip={}; request driver={}, trip={})",
                        command.idempotencyKey(), existing.getDriverId(), existing.getTripId(), command.driverId(), command.tripId());
                throw new DuplicateResourceException(
                        String.format("Idempotency-Key '%s' has already been used for a different reservation", command.idempotencyKey()));
            }
        }

        // 2. Check Driver existence
        Driver driver = driverRepository.findById(command.driverId())
                .orElseThrow(() -> new EntityNotFoundException("Driver", command.driverId()));

        // Check Driver current status
        if (driver.getStatus() != DriverStatus.AVAILABLE) {
            log.warn("Driver {} is not AVAILABLE (status: {})", command.driverId(), driver.getStatus());
            throw new DriverNotAvailableException(
                    String.format("Driver %s is not AVAILABLE for reservation (current status: %s)",
                            command.driverId(), driver.getStatus()));
        }

        // Check if trip already has active reservation
        Optional<DriverReservation> activeTripRes = driverReservationRepository.findActiveByTripId(command.tripId());
        if (activeTripRes.isPresent()) {
            log.warn("Trip {} already has an active reservation with driver {}",
                    command.tripId(), activeTripRes.get().getDriverId());
            throw new DriverNotAvailableException(
                    String.format("Trip %s already has an active driver reservation", command.tripId()));
        }

        // 3. Atomic Reservation via Conditional Update (Single Winner at DB level)
        Instant expiresAt = Instant.now().plusSeconds(command.getEffectiveTtlSeconds());
        boolean reserved = driverRepository.atomicReserve(command.driverId(), command.tripId(), expiresAt);
        if (!reserved) {
            log.warn("Atomic reservation failed for driver {}: concurrent reservation or driver status changed", command.driverId());
            throw new DriverNotAvailableException(
                    String.format("Driver %s could not be reserved (concurrency conflict or driver status changed)", command.driverId()));
        }

        // 4. Create and persist DriverReservation record
        DriverReservation reservation = DriverReservation.create(
                command.driverId(),
                command.tripId(),
                command.idempotencyKey(),
                command.correlationId(),
                expiresAt
        );
        DriverReservation savedReservation = driverReservationRepository.save(reservation);

        // 5. Create and persist OutboxEvent in the same local transaction
        String payloadJson;
        try {
            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("driverId", command.driverId().toString());
            payloadMap.put("tripId", command.tripId().toString());
            payloadMap.put("reservationId", savedReservation.getId().toString());
            payloadMap.put("expiresAt", expiresAt.toString());
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload", e);
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }

        OutboxEvent outboxEvent = OutboxEvent.create(
                "DRIVER",
                command.driverId(),
                "driver.reserved",
                1,
                payloadJson,
                command.correlationId(),
                null
        );
        outboxEventRepository.save(outboxEvent);

        log.info("Driver {} successfully reserved for trip {}. ReservationId={}, expiresAt={}",
                command.driverId(), command.tripId(), savedReservation.getId(), expiresAt);

        return new ReserveDriverResult(
                savedReservation.getId(),
                savedReservation.getDriverId(),
                savedReservation.getTripId(),
                savedReservation.getStatus(),
                savedReservation.getExpiresAt()
        );
    }
}

