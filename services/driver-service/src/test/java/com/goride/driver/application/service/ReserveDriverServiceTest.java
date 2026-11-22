package com.goride.driver.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.exception.DuplicateResourceException;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.driver.application.exception.DriverNotAvailableException;
import com.goride.driver.application.port.in.ReserveDriverCommand;
import com.goride.driver.application.port.in.ReserveDriverResult;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReserveDriverServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private DriverReservationRepository driverReservationRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ReserveDriverService reserveDriverService;

    private UUID driverId;
    private UUID tripId;
    private UUID correlationId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        tripId = UUID.randomUUID();
        correlationId = UUID.randomUUID();
        idempotencyKey = "idemp-" + UUID.randomUUID();
    }

    private Driver createDriver(DriverStatus status) {
        return new Driver(
                driverId,
                UUID.randomUUID(),
                "GPLX-12345678",
                status,
                null,
                null,
                0L,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should successfully reserve driver when available and create outbox event")
    void reserveDriver_Success() throws Exception {
        Driver driver = createDriver(DriverStatus.AVAILABLE);
        when(driverReservationRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverReservationRepository.findActiveByTripId(tripId)).thenReturn(Optional.empty());
        when(driverRepository.atomicReserve(eq(driverId), eq(tripId), any(Instant.class))).thenReturn(true);
        when(driverReservationRepository.save(any(DriverReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReserveDriverCommand command = new ReserveDriverCommand(driverId, tripId, idempotencyKey, correlationId, 30);
        ReserveDriverResult result = reserveDriverService.reserveDriver(command);

        assertThat(result).isNotNull();
        assertThat(result.driverId()).isEqualTo(driverId);
        assertThat(result.tripId()).isEqualTo(tripId);
        assertThat(result.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(result.expiresAt()).isAfter(Instant.now());

        // Verify Outbox Event created
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(outboxCaptor.capture());
        OutboxEvent capturedEvent = outboxCaptor.getValue();
        assertThat(capturedEvent.getAggregateType()).isEqualTo("DRIVER");
        assertThat(capturedEvent.getAggregateId()).isEqualTo(driverId);
        assertThat(capturedEvent.getEventType()).isEqualTo("driver.reserved");
        assertThat(capturedEvent.getCorrelationId()).isEqualTo(correlationId);
        assertThat(capturedEvent.getStatus()).isEqualTo("PENDING");

        JsonNode payloadNode = objectMapper.readTree(capturedEvent.getPayload());
        assertThat(payloadNode.get("driverId").asText()).isEqualTo(driverId.toString());
        assertThat(payloadNode.get("tripId").asText()).isEqualTo(tripId.toString());
        assertThat(payloadNode.get("reservationId").asText()).isEqualTo(result.reservationId().toString());
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when driver does not exist")
    void reserveDriver_DriverNotFound_ThrowsException() {
        when(driverReservationRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(driverRepository.findById(driverId)).thenReturn(Optional.empty());

        ReserveDriverCommand command = new ReserveDriverCommand(driverId, tripId, idempotencyKey, correlationId, 30);

        assertThatThrownBy(() -> reserveDriverService.reserveDriver(command))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Driver not found");

        verify(driverRepository, never()).atomicReserve(any(), any(), any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw DriverNotAvailableException when driver is OFFLINE")
    void reserveDriver_DriverOffline_ThrowsException() {
        Driver driver = createDriver(DriverStatus.OFFLINE);
        when(driverReservationRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));

        ReserveDriverCommand command = new ReserveDriverCommand(driverId, tripId, idempotencyKey, correlationId, 30);

        assertThatThrownBy(() -> reserveDriverService.reserveDriver(command))
                .isInstanceOf(DriverNotAvailableException.class)
                .hasMessageContaining("not AVAILABLE");

        verify(driverRepository, never()).atomicReserve(any(), any(), any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw DriverNotAvailableException when driver is already RESERVED")
    void reserveDriver_DriverAlreadyReserved_ThrowsException() {
        Driver driver = createDriver(DriverStatus.RESERVED);
        when(driverReservationRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));

        ReserveDriverCommand command = new ReserveDriverCommand(driverId, tripId, idempotencyKey, correlationId, 30);

        assertThatThrownBy(() -> reserveDriverService.reserveDriver(command))
                .isInstanceOf(DriverNotAvailableException.class)
                .hasMessageContaining("not AVAILABLE");

        verify(driverRepository, never()).atomicReserve(any(), any(), any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw DriverNotAvailableException when driver is ON_TRIP")
    void reserveDriver_DriverOnTrip_ThrowsException() {
        Driver driver = createDriver(DriverStatus.ON_TRIP);
        when(driverReservationRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));

        ReserveDriverCommand command = new ReserveDriverCommand(driverId, tripId, idempotencyKey, correlationId, 30);

        assertThatThrownBy(() -> reserveDriverService.reserveDriver(command))
                .isInstanceOf(DriverNotAvailableException.class)
                .hasMessageContaining("not AVAILABLE");

        verify(driverRepository, never()).atomicReserve(any(), any(), any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw DriverNotAvailableException when trip already has active reservation")
    void reserveDriver_TripAlreadyHasActiveReservation_ThrowsException() {
        Driver driver = createDriver(DriverStatus.AVAILABLE);
        DriverReservation existingActiveRes = DriverReservation.create(
                UUID.randomUUID(), tripId, "existing-key", correlationId, Instant.now().plusSeconds(30));

        when(driverReservationRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverReservationRepository.findActiveByTripId(tripId)).thenReturn(Optional.of(existingActiveRes));

        ReserveDriverCommand command = new ReserveDriverCommand(driverId, tripId, idempotencyKey, correlationId, 30);

        assertThatThrownBy(() -> reserveDriverService.reserveDriver(command))
                .isInstanceOf(DriverNotAvailableException.class)
                .hasMessageContaining("already has an active driver reservation");

        verify(driverRepository, never()).atomicReserve(any(), any(), any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw DriverNotAvailableException when atomicReserve returns false (concurrency race lost)")
    void reserveDriver_AtomicReserveFails_ThrowsException() {
        Driver driver = createDriver(DriverStatus.AVAILABLE);
        when(driverReservationRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverReservationRepository.findActiveByTripId(tripId)).thenReturn(Optional.empty());
        when(driverRepository.atomicReserve(eq(driverId), eq(tripId), any(Instant.class))).thenReturn(false);

        ReserveDriverCommand command = new ReserveDriverCommand(driverId, tripId, idempotencyKey, correlationId, 30);

        assertThatThrownBy(() -> reserveDriverService.reserveDriver(command))
                .isInstanceOf(DriverNotAvailableException.class)
                .hasMessageContaining("could not be reserved");

        verify(driverReservationRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return existing reservation when idempotency key matches same driver and trip")
    void reserveDriver_IdempotencyReplay_ReturnsExisting() {
        UUID existingReservationId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(25);
        DriverReservation existingReservation = new DriverReservation(
                existingReservationId, driverId, tripId, ReservationStatus.RESERVED,
                idempotencyKey, correlationId, Instant.now(), expiresAt, null, null,
                Instant.now(), Instant.now()
        );

        when(driverReservationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingReservation));

        ReserveDriverCommand command = new ReserveDriverCommand(driverId, tripId, idempotencyKey, correlationId, 30);
        ReserveDriverResult result = reserveDriverService.reserveDriver(command);

        assertThat(result.reservationId()).isEqualTo(existingReservationId);
        assertThat(result.driverId()).isEqualTo(driverId);
        assertThat(result.tripId()).isEqualTo(tripId);
        assertThat(result.status()).isEqualTo(ReservationStatus.RESERVED);

        // Verify NO updates to driver repository and NO new outbox events
        verify(driverRepository, never()).findById(any());
        verify(driverRepository, never()).atomicReserve(any(), any(), any());
        verify(driverReservationRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when idempotency key reused with different payload")
    void reserveDriver_IdempotencyKeyConflictDifferentPayload_ThrowsException() {
        UUID otherTripId = UUID.randomUUID();
        DriverReservation existingReservation = new DriverReservation(
                UUID.randomUUID(), driverId, otherTripId, ReservationStatus.RESERVED,
                idempotencyKey, correlationId, Instant.now(), Instant.now().plusSeconds(30), null, null,
                Instant.now(), Instant.now()
        );

        when(driverReservationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingReservation));

        ReserveDriverCommand command = new ReserveDriverCommand(driverId, tripId, idempotencyKey, correlationId, 30);

        assertThatThrownBy(() -> reserveDriverService.reserveDriver(command))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already been used for a different reservation");

        verify(driverRepository, never()).atomicReserve(any(), any(), any());
        verify(outboxEventRepository, never()).save(any());
    }
}

