package com.goride.trip.application.service;

import com.goride.trip.application.dto.request.AssignDriverRequest;
import com.goride.trip.application.dto.request.CancelTripRequest;
import com.goride.trip.application.dto.request.CreateTripRequest;
import com.goride.trip.application.dto.request.EstimateFareRequest;
import com.goride.trip.application.dto.response.FareEstimateResponse;
import com.goride.trip.application.dto.response.TripDetailResponse;
import com.goride.trip.application.dto.response.TripResponse;
import com.goride.trip.domain.model.ActorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TripService {

    TripResponse createTrip(CreateTripRequest request, UUID correlationId);

    TripResponse getTripById(UUID tripId);

    TripDetailResponse getTripDetail(UUID tripId);

    Page<TripResponse> getTripsByPassenger(UUID passengerId, Pageable pageable);

    FareEstimateResponse estimateFare(EstimateFareRequest request);

    TripResponse assignDriver(UUID tripId, AssignDriverRequest request, String idempotencyKey, UUID correlationId);

    TripResponse acceptTrip(UUID tripId, UUID driverId, UUID correlationId);

    TripResponse startTrip(UUID tripId, UUID driverId, UUID correlationId);

    TripResponse completeTrip(UUID tripId, UUID driverId, UUID correlationId);

    TripResponse cancelTrip(UUID tripId, CancelTripRequest request, UUID actorId, ActorType actorType, UUID correlationId);
}
