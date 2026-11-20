package com.goride.trip.adapter.in.web;

import com.goride.common.dto.ApiResponse;
import com.goride.trip.application.dto.request.AssignDriverRequest;
import com.goride.trip.application.dto.request.CancelTripRequest;
import com.goride.trip.application.dto.request.CreateTripRequest;
import com.goride.trip.application.dto.request.EstimateFareRequest;
import com.goride.trip.application.dto.response.FareEstimateResponse;
import com.goride.trip.application.dto.response.TripDetailResponse;
import com.goride.trip.application.dto.response.TripResponse;
import com.goride.trip.application.service.TripService;
import com.goride.trip.domain.model.ActorType;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping
public class TripController {

    private static final Logger log = LoggerFactory.getLogger(TripController.class);

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    /**
     * Request a new trip ride.
     */
    @PostMapping("/api/v1/trips")
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

        UUID correlationId = parseUuid(correlationIdHeader);
        TripResponse created = tripService.createTrip(request, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    /**
     * Assign a driver to a trip (called by Matching Service Saga).
     * Accessible via both /api/v1/trips/{id}/assign and /internal/trips/{id}/assign-driver.
     */
    @PatchMapping("/api/v1/trips/{id}/assign")
    public ResponseEntity<ApiResponse<TripResponse>> assignDriver(
            @PathVariable UUID id,
            @Valid @RequestBody AssignDriverRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader) {

        UUID correlationId = parseUuid(correlationIdHeader);
        TripResponse response = tripService.assignDriver(id, request, idempotencyKey, correlationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/internal/trips/{id}/assign-driver")
    public ResponseEntity<ApiResponse<TripResponse>> assignDriverInternal(
            @PathVariable UUID id,
            @Valid @RequestBody AssignDriverRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader) {

        return assignDriver(id, request, idempotencyKey, correlationIdHeader);
    }

    /**
     * Driver accepts an assigned trip.
     */
    @PatchMapping("/api/v1/trips/{id}/accept")
    public ResponseEntity<ApiResponse<TripResponse>> acceptTrip(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String driverIdHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader) {

        UUID driverId = parseUuid(driverIdHeader);
        UUID correlationId = parseUuid(correlationIdHeader);
        TripResponse response = tripService.acceptTrip(id, driverId, correlationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Driver starts trip ride (IN_PROGRESS).
     */
    @PatchMapping("/api/v1/trips/{id}/start")
    public ResponseEntity<ApiResponse<TripResponse>> startTrip(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String driverIdHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader) {

        UUID driverId = parseUuid(driverIdHeader);
        UUID correlationId = parseUuid(correlationIdHeader);
        TripResponse response = tripService.startTrip(id, driverId, correlationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Driver completes trip ride (COMPLETED).
     */
    @PatchMapping("/api/v1/trips/{id}/complete")
    public ResponseEntity<ApiResponse<TripResponse>> completeTrip(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String driverIdHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader) {

        UUID driverId = parseUuid(driverIdHeader);
        UUID correlationId = parseUuid(correlationIdHeader);
        TripResponse response = tripService.completeTrip(id, driverId, correlationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Passenger or driver cancels trip ride.
     */
    @PatchMapping("/api/v1/trips/{id}/cancel")
    public ResponseEntity<ApiResponse<TripResponse>> cancelTrip(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelTripRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader) {

        UUID actorId = parseUuid(userIdHeader);
        ActorType actorType = ActorType.USER;
        if (userRolesHeader != null && userRolesHeader.toUpperCase().contains("DRIVER")) {
            actorType = ActorType.DRIVER;
        }
        UUID correlationId = parseUuid(correlationIdHeader);

        TripResponse response = tripService.cancelTrip(id, request, actorId, actorType, correlationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get trip summary by ID.
     */
    @GetMapping("/api/v1/trips/{id}")
    public ResponseEntity<ApiResponse<TripResponse>> getTripById(@PathVariable UUID id) {
        TripResponse response = tripService.getTripById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get detailed trip information including status history.
     */
    @GetMapping("/api/v1/trips/{id}/detail")
    public ResponseEntity<ApiResponse<TripDetailResponse>> getTripDetail(@PathVariable UUID id) {
        TripDetailResponse response = tripService.getTripDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Query trips requested by a specific passenger with pagination.
     */
    @GetMapping("/api/v1/trips/passenger/{passengerId}")
    public ResponseEntity<ApiResponse<Page<TripResponse>>> getTripsByPassenger(
            @PathVariable UUID passengerId,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TripResponse> response = tripService.getTripsByPassenger(passengerId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Estimate ride distance and fare based on pickup and destination coordinates.
     */
    @PostMapping("/api/v1/trips/estimate-fare")
    public ResponseEntity<ApiResponse<FareEstimateResponse>> estimateFare(
            @Valid @RequestBody EstimateFareRequest request) {
        FareEstimateResponse response = tripService.estimateFare(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private UUID parseUuid(String str) {
        if (str == null || str.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(str.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
