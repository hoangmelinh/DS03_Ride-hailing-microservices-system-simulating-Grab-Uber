package com.goride.driver.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.driver.application.exception.DriverNotAvailableException;
import com.goride.driver.application.port.in.ReleaseDriverCommand;
import com.goride.driver.application.port.in.ReleaseDriverResult;
import com.goride.driver.application.port.in.ReleaseDriverUseCase;
import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.DriverReservation;
import com.goride.driver.domain.model.DriverStatus;
import com.goride.driver.domain.model.OutboxEvent;
import com.goride.driver.domain.model.ReservationStatus;
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

@Service
public class ReleaseDriverService implements ReleaseDriverUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReleaseDriverService.class);

    private final DriverRepository driverRepository;
    private final DriverReservationRepository driverReservationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public ReleaseDriverService(DriverRepository driverRepository,
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
    public ReleaseDriverResult releaseDriver(ReleaseDriverCommand command) {
        log.info("Processing release request: driverId={}, tripId={}, idempotencyKey={}, correlationId={}",
                command.driverId(), command.tripId(), command.idempotencyKey(), command.correlationId());

        Driver driver = driverRepository.findById(command.driverId())
                .orElseThrow(() -> new EntityNotFoundException("Driver", command.driverId()));

        DriverReservation reservation = driverReservationRepository
                .findLatestByDriverIdAndTripId(command.driverId(), command.tripId())
                .orElseThrow(() -> new DriverNotAvailableException(
                        String.format("No reservation found for driver %s and trip %s",
                                command.driverId(), command.tripId())));

        // Idempotent replay if already RELEASED
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            log.info("Idempotent release replay for driver {} and trip {}. Already RELEASED at {}",
                    command.driverId(), command.tripId(), reservation.getReleasedAt());
            return new ReleaseDriverResult(
                    command.driverId(),
                    command.tripId(),
                    driver.getStatus(),
                    reservation.getReleasedAt() != null ? reservation.getReleasedAt() : Instant.now()
            );
        }

        if (reservation.getStatus() != ReservationStatus.RESERVED && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new DriverNotAvailableException(
                    String.format("Reservation %s for driver %s and trip %s cannot be released from status %s",
                            reservation.getId(), command.driverId(), command.tripId(), reservation.getStatus()));
        }

        // Atomic conditional release on drivers table
        boolean released = driverRepository.atomicRelease(command.driverId(), command.tripId());
        if (!released) {
            throw new DriverNotAvailableException(
                    String.format("Driver %s cannot be released for trip %s (status=%s, currentTripId=%s)",
                            command.driverId(), command.tripId(), driver.getStatus(), driver.getCurrentTripId()));
        }

        reservation.release();
        DriverReservation savedReservation = driverReservationRepository.save(reservation);

        // Record outbox event driver.released in the same local transaction
        String payloadJson;
        try {
            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("driverId", command.driverId().toString());
            payloadMap.put("tripId", command.tripId().toString());
            payloadMap.put("reservationId", savedReservation.getId().toString());
            payloadMap.put("reason", command.reason() != null ? command.reason() : "SAGA_COMPENSATION");
            payloadMap.put("releasedAt", savedReservation.getReleasedAt().toString());
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize driver.released outbox payload", e);
        }

        OutboxEvent outboxEvent = OutboxEvent.create(
                "DRIVER",
                command.driverId(),
                "driver.released",
                1,
                payloadJson,
                command.correlationId(),
                null
        );
        outboxEventRepository.save(outboxEvent);

        log.info("Driver {} successfully released from trip {}. ReservationId={}",
                command.driverId(), command.tripId(), savedReservation.getId());

        return new ReleaseDriverResult(
                command.driverId(),
                command.tripId(),
                DriverStatus.AVAILABLE,
                savedReservation.getReleasedAt()
        );
    }
}
