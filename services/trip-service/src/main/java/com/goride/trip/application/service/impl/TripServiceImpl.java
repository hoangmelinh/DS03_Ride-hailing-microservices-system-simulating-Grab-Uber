package com.goride.trip.application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.common.exception.InvalidRequestException;
import com.goride.trip.application.dto.event.TripCreatedPayload;
import com.goride.trip.application.dto.request.CreateTripRequest;
import com.goride.trip.application.dto.request.EstimateFareRequest;
import com.goride.trip.application.dto.response.FareEstimateResponse;
import com.goride.trip.application.dto.response.TripDetailResponse;
import com.goride.trip.application.dto.response.TripResponse;
import com.goride.trip.application.dto.response.TripStatusHistoryResponse;
import com.goride.trip.application.service.TripService;
import com.goride.trip.domain.model.ActorType;
import com.goride.trip.domain.model.TripStatus;
import com.goride.trip.infrastructure.persistence.entity.OutboxEventEntity;
import com.goride.trip.infrastructure.persistence.entity.TripEntity;
import com.goride.trip.infrastructure.persistence.entity.TripStatusHistoryEntity;
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
import java.time.Instant;
import java.util.List;
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
    private final ObjectMapper objectMapper;

    public TripServiceImpl(TripRepository tripRepository,
                           TripStatusHistoryRepository tripStatusHistoryRepository,
                           OutboxEventRepository outboxEventRepository,
                           ObjectMapper objectMapper) {
        this.tripRepository = tripRepository;
        this.tripStatusHistoryRepository = tripStatusHistoryRepository;
        this.outboxEventRepository = outboxEventRepository;
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

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload for tripId: {}", trip.getId(), e);
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }

        OutboxEventEntity outboxEvent = new OutboxEventEntity(
                UUID.randomUUID(),
                "TRIP",
                trip.getId(),
                "trip.created",
                jsonPayload,
                effectiveCorrelationId,
                null,
                Instant.now()
        );
        outboxEventRepository.save(outboxEvent);

        log.info("Created trip [id={}, passengerId={}, fare={}, correlationId={}] and queued outbox event 'trip.created'",
                trip.getId(), trip.getPassengerId(), trip.getEstimatedFare(), effectiveCorrelationId);

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

    /**
     * Calculate spherical distance between two coordinate pairs using the Haversine formula.
     */
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

    /**
     * Fare formula:
     * - First 2 km: 15,000 VND
     * - Beyond 2 km: 15,000 + (distance - 2) * 10,000 VND / km
     */
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

