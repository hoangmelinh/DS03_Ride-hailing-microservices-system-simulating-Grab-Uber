package com.goride.trip.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.common.exception.InvalidRequestException;
import com.goride.trip.application.dto.request.CreateTripRequest;
import com.goride.trip.application.dto.request.EstimateFareRequest;
import com.goride.trip.application.dto.response.FareEstimateResponse;
import com.goride.trip.application.dto.response.TripDetailResponse;
import com.goride.trip.application.dto.response.TripResponse;
import com.goride.trip.domain.model.ActorType;
import com.goride.trip.domain.model.OutboxStatus;
import com.goride.trip.domain.model.TripStatus;
import com.goride.trip.infrastructure.persistence.entity.OutboxEventEntity;
import com.goride.trip.infrastructure.persistence.entity.TripEntity;
import com.goride.trip.infrastructure.persistence.entity.TripStatusHistoryEntity;
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

    private ObjectMapper objectMapper;
    private TripServiceImpl tripService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        tripService = new TripServiceImpl(tripRepository, tripStatusHistoryRepository, outboxEventRepository, objectMapper);
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
        assertThat(response.getCurrency()).isEqualTo("VND");

        // Verify Status History record was saved
        ArgumentCaptor<TripStatusHistoryEntity> historyCaptor = ArgumentCaptor.forClass(TripStatusHistoryEntity.class);
        verify(tripStatusHistoryRepository).save(historyCaptor.capture());
        TripStatusHistoryEntity savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getTripId()).isEqualTo(response.getId());
        assertThat(savedHistory.getToStatus()).isEqualTo(TripStatus.REQUESTED.name());
        assertThat(savedHistory.getActorType()).isEqualTo(ActorType.USER);
        assertThat(savedHistory.getActorId()).isEqualTo(passengerId);
        assertThat(savedHistory.getCorrelationId()).isEqualTo(correlationId);

        // Verify Outbox Event was saved (Rule 6: Transactional Outbox)
        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEventEntity savedOutbox = outboxCaptor.getValue();
        assertThat(savedOutbox.getAggregateType()).isEqualTo("TRIP");
        assertThat(savedOutbox.getAggregateId()).isEqualTo(response.getId());
        assertThat(savedOutbox.getEventType()).isEqualTo("trip.created");
        assertThat(savedOutbox.getEventVersion()).isEqualTo(1);
        assertThat(savedOutbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(savedOutbox.getCorrelationId()).isEqualTo(correlationId);
        assertThat(savedOutbox.getPayload()).contains(response.getId().toString());
        assertThat(savedOutbox.getPayload()).contains(passengerId.toString());
        assertThat(savedOutbox.getPayload()).contains("REQUESTED");
    }

    @Test
    @DisplayName("createTrip: Automatically calculates fare when estimatedFare is omitted")
    void createTrip_CalculatesFareAutomatically() {
        UUID passengerId = UUID.randomUUID();

        CreateTripRequest request = new CreateTripRequest(
                passengerId,
                new BigDecimal("10.776889"),
                new BigDecimal("106.700806"),
                "District 1",
                new BigDecimal("10.823099"),
                new BigDecimal("106.629664"),
                "Tan Binh"
        );
        // estimatedFare is null

        when(tripRepository.save(any(TripEntity.class))).thenAnswer(invocation -> {
            TripEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        TripResponse response = tripService.createTrip(request, null);

        assertThat(response).isNotNull();
        assertThat(response.getEstimatedFare()).isNotNull();
        // Distance is ~9.4 km, base fare 15k + extra ~7.4 km * 10k => ~89,000 VND
        assertThat(response.getEstimatedFare()).isGreaterThan(new BigDecimal("15000.00"));
    }

    @Test
    @DisplayName("createTrip: Throws InvalidRequestException when passengerId is missing")
    void createTrip_MissingPassengerId_ThrowsException() {
        CreateTripRequest request = new CreateTripRequest(
                null,
                new BigDecimal("10.776889"),
                new BigDecimal("106.700806"),
                "A",
                new BigDecimal("10.823099"),
                new BigDecimal("106.629664"),
                "B"
        );

        assertThatThrownBy(() -> tripService.createTrip(request, UUID.randomUUID()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Passenger ID is required");
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
        assertThat(response.getPassengerId()).isEqualTo(passengerId);
    }

    @Test
    @DisplayName("getTripById: Throws EntityNotFoundException when not found")
    void getTripById_NotFound() {
        UUID tripId = UUID.randomUUID();
        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.getTripById(tripId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Trip not found with id: " + tripId);
    }

    @Test
    @DisplayName("getTripDetail: Returns trip and its chronological status history")
    void getTripDetail_Success() {
        UUID tripId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        TripEntity entity = new TripEntity(passengerId, new BigDecimal("10.0"), new BigDecimal("106.0"), "From",
                new BigDecimal("10.1"), new BigDecimal("106.1"), "To", new BigDecimal("25000.00"));
        entity.setId(tripId);

        TripStatusHistoryEntity history1 = new TripStatusHistoryEntity(
                tripId, null, "REQUESTED", "Requested", ActorType.USER, passengerId, correlationId);
        TripStatusHistoryEntity history2 = new TripStatusHistoryEntity(
                tripId, "REQUESTED", "MATCHING", "Finding driver", ActorType.SYSTEM, null, correlationId);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(entity));
        when(tripStatusHistoryRepository.findByTripIdOrderByChangedAtAsc(tripId)).thenReturn(List.of(history1, history2));

        TripDetailResponse detail = tripService.getTripDetail(tripId);

        assertThat(detail).isNotNull();
        assertThat(detail.getTrip().getId()).isEqualTo(tripId);
        assertThat(detail.getHistory()).hasSize(2);
        assertThat(detail.getHistory().get(0).getToStatus()).isEqualTo("REQUESTED");
        assertThat(detail.getHistory().get(1).getToStatus()).isEqualTo("MATCHING");
    }

    @Test
    @DisplayName("getTripsByPassenger: Returns paginated trips")
    void getTripsByPassenger_Success() {
        UUID passengerId = UUID.randomUUID();
        TripEntity entity = new TripEntity(passengerId, new BigDecimal("10.0"), new BigDecimal("106.0"), "From",
                new BigDecimal("10.1"), new BigDecimal("106.1"), "To", new BigDecimal("25000.00"));
        entity.setId(UUID.randomUUID());

        Pageable pageable = PageRequest.of(0, 10);
        when(tripRepository.findByPassengerIdOrderByRequestedAtDesc(passengerId, pageable))
                .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));

        Page<TripResponse> page = tripService.getTripsByPassenger(passengerId, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getPassengerId()).isEqualTo(passengerId);
    }

    @Test
    @DisplayName("estimateFare: Short distance (<= 2km) charges base fare 15,000 VND")
    void estimateFare_ShortDistance_ReturnsBaseFare() {
        // Points ~1.1 km apart in HCMC
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
        assertThat(response.getCurrency()).isEqualTo("VND");
    }

    @Test
    @DisplayName("estimateFare: Long distance calculates base fare + per km rate")
    void estimateFare_LongDistance_CalculatesAccurately() {
        // Bitexco to Tan Son Nhat Airport (~9.4 km)
        EstimateFareRequest request = new EstimateFareRequest(
                new BigDecimal("10.776889"),
                new BigDecimal("106.700806"),
                new BigDecimal("10.823099"),
                new BigDecimal("106.629664")
        );

        FareEstimateResponse response = tripService.estimateFare(request);

        assertThat(response).isNotNull();
        assertThat(response.getDistanceKm()).isGreaterThan(new BigDecimal("5.00"));
        assertThat(response.getEstimatedFare()).isGreaterThan(new BigDecimal("50000.00"));
        assertThat(response.getBaseFare()).isEqualByComparingTo("15000.00");
        assertThat(response.getRatePerKm()).isEqualByComparingTo("10000.00");
    }
}

