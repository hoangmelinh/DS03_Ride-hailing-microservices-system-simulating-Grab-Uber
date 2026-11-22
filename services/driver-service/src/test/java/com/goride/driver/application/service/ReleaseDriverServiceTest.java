package com.goride.driver.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.driver.application.exception.DriverNotAvailableException;
import com.goride.driver.application.port.in.ReleaseDriverCommand;
import com.goride.driver.application.port.in.ReleaseDriverResult;
import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.DriverReservation;
import com.goride.driver.domain.model.DriverStatus;
import com.goride.driver.domain.model.OutboxEvent;
import com.goride.driver.domain.model.ReservationStatus;
import com.goride.driver.domain.repository.DriverRepository;
import com.goride.driver.domain.repository.DriverReservationRepository;
import com.goride.driver.domain.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReleaseDriverServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private DriverReservationRepository driverReservationRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ReleaseDriverService releaseDriverService;

    private UUID driverId;
    private UUID tripId;
    private UUID correlationId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        tripId = UUID.randomUUID();
        correlationId = UUID.randomUUID();
        idempotencyKey = "rel-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("Should release reserved driver, update reservation to RELEASED and write driver.released outbox")
    void releaseDriver_Success() {
        Driver driver = new Driver(driverId, UUID.randomUUID(), "GPLX-111", DriverStatus.RESERVED, tripId,
                Instant.now().plusSeconds(30), 1L, Instant.now(), Instant.now());
        DriverReservation reservation = DriverReservation.create(
                driverId, tripId, "res-key", correlationId, Instant.now().plusSeconds(30));

        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverReservationRepository.findLatestByDriverIdAndTripId(driverId, tripId))
                .thenReturn(Optional.of(reservation));
        when(driverRepository.atomicRelease(driverId, tripId)).thenReturn(true);
        when(driverReservationRepository.save(any(DriverReservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReleaseDriverCommand command = new ReleaseDriverCommand(
                driverId, tripId, idempotencyKey, correlationId, "ASSIGN_FAILED");
        ReleaseDriverResult result = releaseDriverService.releaseDriver(command);

        assertThat(result.driverId()).isEqualTo(driverId);
        assertThat(result.tripId()).isEqualTo(tripId);
        assertThat(result.status()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(result.releasedAt()).isNotNull();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(outboxCaptor.capture());
        OutboxEvent event = outboxCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("driver.released");
        assertThat(event.getAggregateType()).isEqualTo("DRIVER");
        assertThat(event.getAggregateId()).isEqualTo(driverId);
        assertThat(event.getPayload()).contains(tripId.toString());
    }

    @Test
    @DisplayName("Should return idempotent result without writing duplicate outbox when already RELEASED")
    void releaseDriver_IdempotentWhenAlreadyReleased() {
        Driver driver = new Driver(driverId, UUID.randomUUID(), "GPLX-111", DriverStatus.AVAILABLE, null,
                null, 2L, Instant.now(), Instant.now());
        Instant releasedAt = Instant.now().minusSeconds(5);
        DriverReservation releasedReservation = new DriverReservation(
                UUID.randomUUID(), driverId, tripId, ReservationStatus.RELEASED,
                "res-key", correlationId, Instant.now().minusSeconds(20), Instant.now().plusSeconds(10),
                null, releasedAt, Instant.now().minusSeconds(20), releasedAt);

        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverReservationRepository.findLatestByDriverIdAndTripId(driverId, tripId))
                .thenReturn(Optional.of(releasedReservation));

        ReleaseDriverCommand command = new ReleaseDriverCommand(
                driverId, tripId, idempotencyKey, correlationId, "RETRY");
        ReleaseDriverResult result = releaseDriverService.releaseDriver(command);

        assertThat(result.status()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(result.releasedAt()).isEqualTo(releasedAt);
        verify(driverRepository, never()).atomicRelease(any(), any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw DriverNotAvailableException when releasing with mismatched tripId")
    void releaseDriver_MismatchedTrip_ThrowsException() {
        Driver driver = new Driver(driverId, UUID.randomUUID(), "GPLX-111", DriverStatus.RESERVED, UUID.randomUUID(),
                Instant.now().plusSeconds(30), 1L, Instant.now(), Instant.now());

        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverReservationRepository.findLatestByDriverIdAndTripId(driverId, tripId))
                .thenReturn(Optional.empty());

        ReleaseDriverCommand command = new ReleaseDriverCommand(
                driverId, tripId, idempotencyKey, correlationId, "WRONG_TRIP");

        assertThatThrownBy(() -> releaseDriverService.releaseDriver(command))
                .isInstanceOf(DriverNotAvailableException.class);
        verify(driverRepository, never()).atomicRelease(any(), any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when driver does not exist")
    void releaseDriver_DriverNotFound_ThrowsException() {
        when(driverRepository.findById(driverId)).thenReturn(Optional.empty());

        ReleaseDriverCommand command = new ReleaseDriverCommand(
                driverId, tripId, idempotencyKey, correlationId, "ASSIGN_FAILED");

        assertThatThrownBy(() -> releaseDriverService.releaseDriver(command))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
