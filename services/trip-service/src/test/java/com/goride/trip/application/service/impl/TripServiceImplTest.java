package com.goride.trip.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.common.exception.InvalidRequestException;
import com.goride.trip.application.dto.request.AssignDriverRequest;
import com.goride.trip.application.dto.request.CancelTripRequest;
import com.goride.trip.application.dto.request.CreateTripRequest;
import com.goride.trip.application.dto.request.EstimateFareRequest;
import com.goride.trip.application.dto.response.FareEstimateResponse;
import com.goride.trip.application.dto.response.TripDetailResponse;
import com.goride.trip.application.dto.response.TripResponse;
import com.goride.trip.domain.model.ActorType;
import com.goride.trip.domain.model.IdempotencyStatus;
import com.goride.trip.domain.model.OutboxStatus;
import com.goride.trip.domain.model.TripStatus;
import com.goride.trip.infrastructure.persistence.entity.IdempotencyRecordEntity;
import com.goride.trip.infrastructure.persistence.entity.OutboxEventEntity;
import com.goride.trip.infrastructure.persistence.entity.TripEntity;
import com.goride.trip.infrastructure.persistence.entity.TripStatusHistoryEntity;
import com.goride.trip.infrastructure.persistence.repository.IdempotencyRecordRepository;
import com.goride.trip.infrastructure.persistence.repository.OutboxEventRepository;
import com.goride.trip.infrastructure.persistence.repository.TripRepository;
import com.goride.trip.infrastructure.persistence.repository.TripStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceImplTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripStatusHistoryRepository tripStatusHistoryRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    private ObjectMapper objectMapper;
    private TripServiceImpl tripService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        tripService = new TripServiceImpl(tripRepository, tripStatusHistoryRepository, outboxEventRepository, idempotencyRecordRepository, objectMapper);
    }

    @Test
    @DisplayName("createTrip: Persists trip, status history, and transactional outbox event 'trip.created'")
    void createTrip_Success_WithExplicitFare() {
        UUID passengerId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        CreateTripRequest request = new CreateTripRequest(
                passengerId,
                new BigDecimal("10.776889"),
                new BigDecimal("106.700806"),
                "Bitexco Financial Tower, District 1",
                new BigDecimal("10.823099"),
                new BigDecimal("106.629664"),
                "Tan Son Nhat Airport"
        );
        request.setEstimatedFare(new BigDecimal("95000.00"));

        when(tripRepository.save(any(TripEntity.class))).thenAnswer(invocation -> {
            TripEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            return entity;
        });

        TripResponse response = tripService.createTrip(request, correlationId);

        assertThat(response).isNotNull();
        assertThat(response.getPassengerId()).isEqualTo(passengerId);
        assertThat(response.getStatus()).isEqualTo(TripStatus.REQUESTED);
        assertThat(response.getEstimatedFare()).isEqualByComparingTo("95000.00");

        // Verify Outbox Event was saved (Rule 6: Transactional Outbox)
        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEventEntity savedOutbox = outboxCaptor.getValue();
        assertThat(savedOutbox.getAggregateType()).isEqualTo("TRIP");
        assertThat(savedOutbox.getEventType()).isEqualTo("trip.created");
        assertThat(savedOutbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(savedOutbox.getCorrelationId()).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("assignDriver: Successfully assigns driver, transitions status to DRIVER_ASSIGNED, and creates outbox event")
    void assignDriver_Success() {
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        TripEntity trip = new TripEntity(UUID.randomUUID(), new BigDecimal("10.0"), new BigDecimal("106.0"), "From",
                new BigDecimal("10.1"), new BigDecimal("106.1"), "To", new BigDecimal("50000.00"));
        trip.setId(tripId);
        trip.setStatus(TripStatus.REQUESTED);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(TripEntity.class))).thenAnswer(i -> i.getArgument(0));

        AssignDriverRequest request = new AssignDriverRequest(driverId);
        TripResponse response = tripService.assignDriver(tripId, request, "IDEMP-001", correlationId);

        assertThat(response).isNotNull();
        assertThat(response.getDriverId()).isEqualTo(driverId);
        assertThat(response.getStatus()).isEqualTo(TripStatus.DRIVER_ASSIGNED);
        assertThat(response.getAssignedAt()).isNotNull();

        // Verify outbox event 'trip.driver-assigned'
        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("trip.driver-assigned");

        // Verify idempotency record saved
        verify(idempotencyRecordRepository).save(any(IdempotencyRecordEntity.class));
    }

    @Test
    @DisplayName("assignDriver: Reusing idempotency key returns cached response")
    void assignDriver_IdempotentWithKey() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        String key = "IDEMP-KEY-999";

        TripResponse cached = new TripResponse();
        cached.setId(tripId);
        cached.setDriverId(driverId);
        cached.setStatus(TripStatus.DRIVER_ASSIGNED);

        IdempotencyRecordEntity record = new IdempotencyRecordEntity(key, "ASSIGN_DRIVER", tripId.toString(),
                IdempotencyStatus.COMPLETED, Instant.now().plusSeconds(3600));
        record.setResponseBody(objectMapper.writeValueAsString(cached));

        when(idempotencyRecordRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

        TripResponse response = tripService.assignDriver(tripId, new AssignDriverRequest(driverId), key, null);

        assertThat(response.getId()).isEqualTo(tripId);
        assertThat(response.getStatus()).isEqualTo(TripStatus.DRIVER_ASSIGNED);
    }

    @Test
    @DisplayName("acceptTrip: Driver accepts trip, transitions to ACCEPTED, and creates outbox event")
    void acceptTrip_Success() {
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        TripEntity trip = new TripEntity();
        trip.setId(tripId);
        trip.setDriverId(driverId);
        trip.setStatus(TripStatus.DRIVER_ASSIGNED);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(TripEntity.class))).thenAnswer(i -> i.getArgument(0));

        TripResponse response = tripService.acceptTrip(tripId, driverId, null);

        assertThat(response.getStatus()).isEqualTo(TripStatus.ACCEPTED);
        assertThat(response.getAcceptedAt()).isNotNull();

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("trip.accepted");
    }

    @Test
    @DisplayName("startTrip: Driver starts trip, transitions to IN_PROGRESS, and creates outbox event")
    void startTrip_Success() {
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        TripEntity trip = new TripEntity();
        trip.setId(tripId);
        trip.setDriverId(driverId);
        trip.setStatus(TripStatus.ACCEPTED);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(TripEntity.class))).thenAnswer(i -> i.getArgument(0));

        TripResponse response = tripService.startTrip(tripId, driverId, null);

        assertThat(response.getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(response.getStartedAt()).isNotNull();

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("trip.started");
    }

    @Test
    @DisplayName("completeTrip: Driver completes trip, sets final fare, transitions to COMPLETED, and creates outbox event for Payment")
    void completeTrip_Success() {
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();

        TripEntity trip = new TripEntity();
        trip.setId(tripId);
        trip.setPassengerId(passengerId);
        trip.setDriverId(driverId);
        trip.setStatus(TripStatus.IN_PROGRESS);
        trip.setEstimatedFare(new BigDecimal("85000.00"));

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(TripEntity.class))).thenAnswer(i -> i.getArgument(0));

        TripResponse response = tripService.completeTrip(tripId, driverId, null);

        assertThat(response.getStatus()).isEqualTo(TripStatus.COMPLETED);
        assertThat(response.getFinalFare()).isEqualByComparingTo("85000.00");
        assertThat(response.getCompletedAt()).isNotNull();

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEventEntity outbox = outboxCaptor.getValue();
        assertThat(outbox.getEventType()).isEqualTo("trip.completed");
        assertThat(outbox.getPayload()).contains(tripId.toString());
        assertThat(outbox.getPayload()).contains(passengerId.toString());
        assertThat(outbox.getPayload()).contains("85000.00");
    }

    @Test
    @DisplayName("cancelTrip: Successfully cancels trip and generates outbox event")
    void cancelTrip_Success() {
        UUID tripId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();

        TripEntity trip = new TripEntity();
        trip.setId(tripId);
        trip.setPassengerId(passengerId);
        trip.setStatus(TripStatus.REQUESTED);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(TripEntity.class))).thenAnswer(i -> i.getArgument(0));

        CancelTripRequest request = new CancelTripRequest("Passenger changed plans");
        TripResponse response = tripService.cancelTrip(tripId, request, passengerId, ActorType.USER, null);

        assertThat(response.getStatus()).isEqualTo(TripStatus.CANCELLED);
        assertThat(response.getCancellationReason()).isEqualTo("Passenger changed plans");

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("trip.cancelled");
    }

    @Test
    @DisplayName("getTripById: Returns trip response when found")
    void getTripById_Success() {
        UUID tripId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        TripEntity entity = new TripEntity(passengerId, new BigDecimal("10.0"), new BigDecimal("106.0"), "From",
                new BigDecimal("10.1"), new BigDecimal("106.1"), "To", new BigDecimal("25000.00"));
        entity.setId(tripId);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(entity));

        TripResponse response = tripService.getTripById(tripId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(tripId);
    }

    @Test
    @DisplayName("getTripById: Throws EntityNotFoundException when not found")
    void getTripById_NotFound() {
        UUID tripId = UUID.randomUUID();
        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.getTripById(tripId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("getTripDetail: Returns trip and its chronological status history")
    void getTripDetail_Success() {
        UUID tripId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();

        TripEntity entity = new TripEntity(passengerId, new BigDecimal("10.0"), new BigDecimal("106.0"), "From",
                new BigDecimal("10.1"), new BigDecimal("106.1"), "To", new BigDecimal("25000.00"));
        entity.setId(tripId);

        TripStatusHistoryEntity history = new TripStatusHistoryEntity(
                tripId, null, "REQUESTED", "Requested", ActorType.USER, passengerId, UUID.randomUUID());

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(entity));
        when(tripStatusHistoryRepository.findByTripIdOrderByChangedAtAsc(tripId)).thenReturn(List.of(history));

        TripDetailResponse detail = tripService.getTripDetail(tripId);

        assertThat(detail).isNotNull();
        assertThat(detail.getTrip().getId()).isEqualTo(tripId);
        assertThat(detail.getHistory()).hasSize(1);
    }

    @Test
    @DisplayName("getTripsByPassenger: Returns paginated trips")
    void getTripsByPassenger_Success() {
        UUID passengerId = UUID.randomUUID();
        TripEntity entity = new TripEntity();
        entity.setId(UUID.randomUUID());
        entity.setPassengerId(passengerId);

        Pageable pageable = PageRequest.of(0, 10);
        when(tripRepository.findByPassengerIdOrderByRequestedAtDesc(passengerId, pageable))
                .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));

        Page<TripResponse> page = tripService.getTripsByPassenger(passengerId, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("estimateFare: Short distance (<= 2km) charges base fare 15,000 VND")
    void estimateFare_ShortDistance_ReturnsBaseFare() {
        EstimateFareRequest request = new EstimateFareRequest(
                new BigDecimal("10.776889"),
                new BigDecimal("106.700806"),
                new BigDecimal("10.772000"),
                new BigDecimal("106.695000")
        );

        FareEstimateResponse response = tripService.estimateFare(request);

        assertThat(response).isNotNull();
        assertThat(response.getDistanceKm()).isLessThanOrEqualTo(new BigDecimal("2.00"));
        assertThat(response.getEstimatedFare()).isEqualByComparingTo(new BigDecimal("15000.00"));
    }
}
