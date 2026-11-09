package com.goride.trip.adapter.in.web;

import com.goride.common.dto.ApiResponse;
import com.goride.trip.application.dto.request.CreateTripRequest;
import com.goride.trip.application.dto.request.EstimateFareRequest;
import com.goride.trip.application.dto.response.FareEstimateResponse;
import com.goride.trip.application.dto.response.TripDetailResponse;
import com.goride.trip.application.dto.response.TripResponse;
import com.goride.trip.application.service.TripService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private static final Logger log = LoggerFactory.getLogger(TripController.class);

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    /**
     * Request a new trip ride.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TripResponse>> createTrip(
            @Valid @RequestBody CreateTripRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {

        if (request.getPassengerId() == null && userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                request.setPassengerId(UUID.fromString(userIdHeader.trim()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid X-User-Id header value: {}", userIdHeader);
            }
        }

        UUID correlationId = null;
        if (correlationIdHeader != null && !correlationIdHeader.isBlank()) {
            try {
                correlationId = UUID.fromString(correlationIdHeader.trim());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid X-Correlation-Id header value: {}", correlationIdHeader);
            }
        }

        TripResponse created = tripService.createTrip(request, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    /**
     * Get trip summary by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TripResponse>> getTripById(@PathVariable UUID id) {
        TripResponse response = tripService.getTripById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get detailed trip information including status history.
     */
    @GetMapping("/{id}/detail")
    public ResponseEntity<ApiResponse<TripDetailResponse>> getTripDetail(@PathVariable UUID id) {
        TripDetailResponse response = tripService.getTripDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Query trips requested by a specific passenger with pagination.
     */
    @GetMapping("/passenger/{passengerId}")
    public ResponseEntity<ApiResponse<Page<TripResponse>>> getTripsByPassenger(
            @PathVariable UUID passengerId,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TripResponse> response = tripService.getTripsByPassenger(passengerId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Estimate ride distance and fare based on pickup and destination coordinates.
     */
    @PostMapping("/estimate-fare")
    public ResponseEntity<ApiResponse<FareEstimateResponse>> estimateFare(
            @Valid @RequestBody EstimateFareRequest request) {
        FareEstimateResponse response = tripService.estimateFare(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

