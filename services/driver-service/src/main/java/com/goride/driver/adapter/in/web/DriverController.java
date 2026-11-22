package com.goride.driver.adapter.in.web;

import com.goride.common.dto.ApiResponse;
import com.goride.driver.adapter.in.web.dto.AvailableDriverResponse;
import com.goride.driver.adapter.in.web.dto.DriverLocationResponse;
import com.goride.driver.adapter.in.web.dto.DriverResponse;
import com.goride.driver.adapter.in.web.dto.DriverStatusResponse;
import com.goride.driver.adapter.in.web.dto.RegisterDriverRequest;
import com.goride.driver.adapter.in.web.dto.ReleaseDriverRequest;
import com.goride.driver.adapter.in.web.dto.ReleaseDriverResponse;
import com.goride.driver.adapter.in.web.dto.ReserveDriverRequest;
import com.goride.driver.adapter.in.web.dto.ReserveDriverResponse;
import com.goride.driver.adapter.in.web.dto.UpdateDriverLocationRequest;
import com.goride.driver.adapter.in.web.dto.UpdateDriverStatusRequest;
import com.goride.driver.application.port.in.AvailableDriverResult;
import com.goride.driver.application.port.in.DriverResult;
import com.goride.driver.application.port.in.GetAvailableDriversUseCase;
import com.goride.driver.application.port.in.RegisterDriverCommand;
import com.goride.driver.application.port.in.RegisterDriverUseCase;
import com.goride.driver.application.port.in.ReleaseDriverCommand;
import com.goride.driver.application.port.in.ReleaseDriverResult;
import com.goride.driver.application.port.in.ReleaseDriverUseCase;
import com.goride.driver.application.port.in.ReserveDriverCommand;
import com.goride.driver.application.port.in.ReserveDriverResult;
import com.goride.driver.application.port.in.ReserveDriverUseCase;
import com.goride.driver.application.port.in.UpdateDriverLocationCommand;
import com.goride.driver.application.port.in.UpdateDriverLocationUseCase;
import com.goride.driver.application.port.in.UpdateDriverStatusCommand;
import com.goride.driver.application.port.in.UpdateDriverStatusUseCase;
import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.DriverLocation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private final RegisterDriverUseCase registerDriverUseCase;
    private final UpdateDriverStatusUseCase updateDriverStatusUseCase;
    private final UpdateDriverLocationUseCase updateDriverLocationUseCase;
    private final ReserveDriverUseCase reserveDriverUseCase;
    private final ReleaseDriverUseCase releaseDriverUseCase;
    private final GetAvailableDriversUseCase getAvailableDriversUseCase;

    public DriverController(RegisterDriverUseCase registerDriverUseCase,
                            UpdateDriverStatusUseCase updateDriverStatusUseCase,
                            UpdateDriverLocationUseCase updateDriverLocationUseCase,
                            ReserveDriverUseCase reserveDriverUseCase,
                            ReleaseDriverUseCase releaseDriverUseCase,
                            GetAvailableDriversUseCase getAvailableDriversUseCase) {
        this.registerDriverUseCase = registerDriverUseCase;
        this.updateDriverStatusUseCase = updateDriverStatusUseCase;
        this.updateDriverLocationUseCase = updateDriverLocationUseCase;
        this.reserveDriverUseCase = reserveDriverUseCase;
        this.releaseDriverUseCase = releaseDriverUseCase;
        this.getAvailableDriversUseCase = getAvailableDriversUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DriverResponse>> registerDriver(@Valid @RequestBody RegisterDriverRequest request) {
        RegisterDriverCommand command = new RegisterDriverCommand(
                request.userId(),
                request.licenseNumber(),
                request.vehiclePlate(),
                request.vehicleModel()
        );

        DriverResult result = registerDriverUseCase.registerDriver(command);

        DriverResponse response = new DriverResponse(
                result.driver().getId(),
                result.driver().getUserId(),
                result.driver().getLicenseNumber(),
                result.driver().getStatus(),
                new DriverResponse.VehicleResponse(
                        result.vehicle().getId(),
                        result.vehicle().getPlateNumber(),
                        result.vehicle().getModel(),
                        result.vehicle().getCapacity(),
                        result.vehicle().getStatus()
                ),
                result.driver().getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<DriverStatusResponse>> updateDriverStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateDriverStatusRequest request
    ) {
        UpdateDriverStatusCommand command = new UpdateDriverStatusCommand(id, request.status());
        Driver updatedDriver = updateDriverStatusUseCase.updateDriverStatus(command);

        DriverStatusResponse response = new DriverStatusResponse(
                updatedDriver.getId(),
                updatedDriver.getUserId(),
                updatedDriver.getLicenseNumber(),
                updatedDriver.getStatus(),
                updatedDriver.getCurrentTripId(),
                updatedDriver.getUpdatedAt()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}/location")
    public ResponseEntity<ApiResponse<DriverLocationResponse>> updateDriverLocation(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateDriverLocationRequest request
    ) {
        UpdateDriverLocationCommand command = new UpdateDriverLocationCommand(
                id,
                request.latitude(),
                request.longitude()
        );
        DriverLocation updatedLocation = updateDriverLocationUseCase.updateLocation(command);

        DriverLocationResponse response = new DriverLocationResponse(
                updatedLocation.getDriverId(),
                updatedLocation.getLatitude(),
                updatedLocation.getLongitude(),
                updatedLocation.getUpdatedAt()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reserve")
    public ResponseEntity<ApiResponse<ReserveDriverResponse>> reserveDriver(
            @PathVariable("id") UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader,
            @Valid @RequestBody ReserveDriverRequest request
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header must not be blank");
        }

        UUID correlationId;
        if (correlationIdHeader != null && !correlationIdHeader.isBlank()) {
            try {
                correlationId = UUID.fromString(correlationIdHeader.trim());
            } catch (IllegalArgumentException e) {
                correlationId = UUID.randomUUID();
            }
        } else {
            correlationId = UUID.randomUUID();
        }

        ReserveDriverCommand command = new ReserveDriverCommand(
                id,
                request.tripId(),
                idempotencyKey.trim(),
                correlationId,
                request.ttlSeconds()
        );

        ReserveDriverResult result = reserveDriverUseCase.reserveDriver(command);

        ReserveDriverResponse response = new ReserveDriverResponse(
                result.reservationId(),
                result.driverId(),
                result.tripId(),
                result.status().name(),
                result.expiresAt()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<ApiResponse<ReleaseDriverResponse>> releaseDriver(
            @PathVariable("id") UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader,
            @Valid @RequestBody ReleaseDriverRequest request
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header must not be blank");
        }

        UUID correlationId;
        if (correlationIdHeader != null && !correlationIdHeader.isBlank()) {
            try {
                correlationId = UUID.fromString(correlationIdHeader.trim());
            } catch (IllegalArgumentException e) {
                correlationId = UUID.randomUUID();
            }
        } else {
            correlationId = UUID.randomUUID();
        }

        ReleaseDriverCommand command = new ReleaseDriverCommand(
                id,
                request.tripId(),
                idempotencyKey.trim(),
                correlationId,
                request.reason()
        );

        ReleaseDriverResult result = releaseDriverUseCase.releaseDriver(command);

        ReleaseDriverResponse response = new ReleaseDriverResponse(
                result.driverId(),
                result.tripId(),
                result.status().name(),
                result.releasedAt()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<AvailableDriverResponse>>> getAvailableDrivers(
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        List<AvailableDriverResponse> response = getAvailableDriversUseCase.getAvailableDrivers(limit)
                .stream()
                .map(r -> new AvailableDriverResponse(
                        r.driverId(),
                        r.status().name(),
                        r.latitude(),
                        r.longitude(),
                        r.locationUpdatedAt()
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
