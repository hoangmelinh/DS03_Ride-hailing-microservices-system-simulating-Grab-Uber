package com.goride.trip.application.service;

import com.goride.trip.application.dto.request.CreateTripRequest;
import com.goride.trip.application.dto.request.EstimateFareRequest;
import com.goride.trip.application.dto.response.FareEstimateResponse;
import com.goride.trip.application.dto.response.TripDetailResponse;
import com.goride.trip.application.dto.response.TripResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TripService {

    TripResponse createTrip(CreateTripRequest request, UUID correlationId);

    TripResponse getTripById(UUID tripId);

    TripDetailResponse getTripDetail(UUID tripId);

    Page<TripResponse> getTripsByPassenger(UUID passengerId, Pageable pageable);

    FareEstimateResponse estimateFare(EstimateFareRequest request);
}

