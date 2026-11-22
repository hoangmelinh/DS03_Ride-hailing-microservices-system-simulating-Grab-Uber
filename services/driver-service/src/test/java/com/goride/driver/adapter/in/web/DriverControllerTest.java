package com.goride.driver.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.exception.DuplicateResourceException;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.common.exception.InvalidRequestException;
import com.goride.driver.adapter.in.web.dto.RegisterDriverRequest;
import com.goride.driver.adapter.in.web.dto.ReleaseDriverRequest;
import com.goride.driver.adapter.in.web.dto.ReserveDriverRequest;
import com.goride.driver.adapter.in.web.dto.UpdateDriverLocationRequest;
import com.goride.driver.adapter.in.web.dto.UpdateDriverStatusRequest;
import com.goride.driver.application.exception.DriverNotAvailableException;
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
import com.goride.driver.domain.model.DriverStatus;
import com.goride.driver.domain.model.ReservationStatus;
import com.goride.driver.domain.model.Vehicle;
import com.goride.driver.domain.model.VehicleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DriverController.class)
@AutoConfigureMockMvc(addFilters = false)
class DriverControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegisterDriverUseCase registerDriverUseCase;

    @MockBean
    private UpdateDriverStatusUseCase updateDriverStatusUseCase;

    @MockBean
    private UpdateDriverLocationUseCase updateDriverLocationUseCase;

    @MockBean
    private ReserveDriverUseCase reserveDriverUseCase;

    @MockBean
    private ReleaseDriverUseCase releaseDriverUseCase;

    @MockBean
    private GetAvailableDriversUseCase getAvailableDriversUseCase;

    // =========================================================================
    // POST /api/v1/drivers tests
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/drivers should return 201 when request is valid with userId")
    void shouldReturn201WhenRegisterDriverSuccessfully() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String licenseNumber = "GPLX-123456";
        String vehiclePlate = "30A-99999";
        String vehicleModel = "Toyota Vios";

        Driver driver = new Driver(
                driverId,
                userId,
                licenseNumber,
                DriverStatus.OFFLINE,
                null,
                null,
                0L,
                Instant.now(),
                Instant.now()
        );

        Vehicle vehicle = new Vehicle(
                UUID.randomUUID(),
                driverId,
                vehiclePlate,
                "Toyota",
                vehicleModel,
                "White",
                4,
                VehicleStatus.ACTIVE,
                Instant.now(),
                Instant.now()
        );

        when(registerDriverUseCase.registerDriver(any(RegisterDriverCommand.class)))
                .thenReturn(new DriverResult(driver, vehicle));

        RegisterDriverRequest request = new RegisterDriverRequest(
                userId,
                licenseNumber,
                vehiclePlate,
                vehicleModel
        );

        mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(driverId.toString()))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.licenseNumber").value(licenseNumber))
                .andExpect(jsonPath("$.data.status").value("OFFLINE"))
                .andExpect(jsonPath("$.data.vehicle.plateNumber").value(vehiclePlate))
                .andExpect(jsonPath("$.data.vehicle.model").value(vehicleModel));
    }

    @Test
    @DisplayName("POST /api/v1/drivers should return 400 when missing userId")
    void shouldReturn400WhenRegisterDriverMissingUserId() throws Exception {
        RegisterDriverRequest request = new RegisterDriverRequest(
                null,
                "GPLX-123456",
                "30A-99999",
                "Toyota Vios"
        );

        mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/drivers should return 400 when license or vehicle info is blank")
    void shouldReturn400WhenRegisterDriverMissingLicenseOrVehicleInfo() throws Exception {
        RegisterDriverRequest request = new RegisterDriverRequest(
                UUID.randomUUID(),
                "",
                "",
                ""
        );

        mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/drivers should return 409 when userId already exists")
    void shouldReturn409WhenRegisterDriverUserIdAlreadyExists() throws Exception {
        UUID userId = UUID.randomUUID();

        when(registerDriverUseCase.registerDriver(any(RegisterDriverCommand.class)))
                .thenThrow(new DuplicateResourceException("userId", userId));

        RegisterDriverRequest request = new RegisterDriverRequest(
                userId,
                "GPLX-123456",
                "30A-99999",
                "Toyota Vios"
        );

        mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    // =========================================================================
    // PATCH /api/v1/drivers/{id}/status tests
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/v1/drivers/{id}/status should return 200 and updated status AVAILABLE")
    void shouldReturn200WhenUpdateStatusOnline() throws Exception {
        UUID driverId = UUID.randomUUID();
        Driver updatedDriver = new Driver(
                driverId,
                UUID.randomUUID(),
                "GPLX123456",
                DriverStatus.AVAILABLE,
                null,
                null,
                0L,
                Instant.now(),
                Instant.now()
        );

        when(updateDriverStatusUseCase.updateDriverStatus(any(UpdateDriverStatusCommand.class)))
                .thenReturn(updatedDriver);

        UpdateDriverStatusRequest request = new UpdateDriverStatusRequest("ONLINE");

        mockMvc.perform(patch("/api/v1/drivers/" + driverId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(driverId.toString()))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("PATCH /api/v1/drivers/{id}/status should return 404 when driver not found")
    void shouldReturn404WhenDriverNotFound() throws Exception {
        UUID driverId = UUID.randomUUID();

        when(updateDriverStatusUseCase.updateDriverStatus(any(UpdateDriverStatusCommand.class)))
                .thenThrow(new EntityNotFoundException("Driver", driverId));

        UpdateDriverStatusRequest request = new UpdateDriverStatusRequest("ONLINE");

        mockMvc.perform(patch("/api/v1/drivers/" + driverId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/v1/drivers/{id}/status should return 400 when status is blank")
    void shouldReturn400WhenStatusIsBlank() throws Exception {
        UUID driverId = UUID.randomUUID();
        UpdateDriverStatusRequest request = new UpdateDriverStatusRequest("");

        mockMvc.perform(patch("/api/v1/drivers/" + driverId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PATCH /api/v1/drivers/{id}/status should return 400 when service throws InvalidRequestException")
    void shouldReturn400WhenInvalidStatusThrown() throws Exception {
        UUID driverId = UUID.randomUUID();

        when(updateDriverStatusUseCase.updateDriverStatus(any(UpdateDriverStatusCommand.class)))
                .thenThrow(new InvalidRequestException("Invalid driver status: INVALID"));

        UpdateDriverStatusRequest request = new UpdateDriverStatusRequest("INVALID");

        mockMvc.perform(patch("/api/v1/drivers/" + driverId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // =========================================================================
    // PUT /api/v1/drivers/{id}/location tests
    // =========================================================================

    @Test
    @DisplayName("PUT /api/v1/drivers/{id}/location should return 200 and updated coordinates")
    void shouldReturn200WhenUpdateLocationSuccessfully() throws Exception {
        UUID driverId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("10.762622");
        BigDecimal lng = new BigDecimal("106.660172");
        DriverLocation location = new DriverLocation(driverId, lat, lng, Instant.now());

        when(updateDriverLocationUseCase.updateLocation(any(UpdateDriverLocationCommand.class)))
                .thenReturn(location);

        UpdateDriverLocationRequest request = new UpdateDriverLocationRequest(lat, lng);

        mockMvc.perform(put("/api/v1/drivers/" + driverId + "/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.driverId").value(driverId.toString()))
                .andExpect(jsonPath("$.data.latitude").value(10.762622))
                .andExpect(jsonPath("$.data.longitude").value(106.660172))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
    }

    @Test
    @DisplayName("PUT /api/v1/drivers/{id}/location should return 404 when driver not found")
    void shouldReturn404WhenDriverNotFoundOnUpdateLocation() throws Exception {
        UUID driverId = UUID.randomUUID();

        when(updateDriverLocationUseCase.updateLocation(any(UpdateDriverLocationCommand.class)))
                .thenThrow(new EntityNotFoundException("Driver", driverId));

        UpdateDriverLocationRequest request = new UpdateDriverLocationRequest(
                new BigDecimal("10.762622"),
                new BigDecimal("106.660172")
        );

        mockMvc.perform(put("/api/v1/drivers/" + driverId + "/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /api/v1/drivers/{id}/location should return 400 when coordinates out of range")
    void shouldReturn400WhenCoordinatesOutOfRange() throws Exception {
        UUID driverId = UUID.randomUUID();
        UpdateDriverLocationRequest request = new UpdateDriverLocationRequest(
                new BigDecimal("99.999999"),
                new BigDecimal("199.999999")
        );

        mockMvc.perform(put("/api/v1/drivers/" + driverId + "/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // =========================================================================
    // POST /api/v1/drivers/{id}/reserve tests
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/drivers/{id}/reserve should return 200 when reservation is successful")
    void shouldReturn200WhenReserveDriverSuccessfully() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(30);

        ReserveDriverResult mockResult = new ReserveDriverResult(
                reservationId, driverId, tripId, ReservationStatus.RESERVED, expiresAt
        );

        when(reserveDriverUseCase.reserveDriver(any(ReserveDriverCommand.class))).thenReturn(mockResult);

        ReserveDriverRequest request = new ReserveDriverRequest(tripId, 30);

        mockMvc.perform(post("/api/v1/drivers/" + driverId + "/reserve")
                        .header("Idempotency-Key", "idemp-key-123")
                        .header("X-Correlation-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reservationId").value(reservationId.toString()))
                .andExpect(jsonPath("$.data.driverId").value(driverId.toString()))
                .andExpect(jsonPath("$.data.tripId").value(tripId.toString()))
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/drivers/{id}/reserve should return 400 when Idempotency-Key header is missing")
    void shouldReturn400WhenIdempotencyKeyHeaderIsMissing() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        ReserveDriverRequest request = new ReserveDriverRequest(tripId, 30);

        mockMvc.perform(post("/api/v1/drivers/" + driverId + "/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/drivers/{id}/reserve should return 400 when tripId is missing")
    void shouldReturn400WhenTripIdIsMissing() throws Exception {
        UUID driverId = UUID.randomUUID();
        ReserveDriverRequest request = new ReserveDriverRequest(null, 30);

        mockMvc.perform(post("/api/v1/drivers/" + driverId + "/reserve")
                        .header("Idempotency-Key", "idemp-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/drivers/{id}/reserve should return 404 when driver is not found")
    void shouldReturn404WhenDriverNotFoundForReservation() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        ReserveDriverRequest request = new ReserveDriverRequest(tripId, 30);

        when(reserveDriverUseCase.reserveDriver(any(ReserveDriverCommand.class)))
                .thenThrow(new EntityNotFoundException("Driver", driverId));

        mockMvc.perform(post("/api/v1/drivers/" + driverId + "/reserve")
                        .header("Idempotency-Key", "idemp-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/drivers/{id}/reserve should return 409 when driver is not available")
    void shouldReturn409WhenDriverIsNotAvailableForReservation() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        ReserveDriverRequest request = new ReserveDriverRequest(tripId, 30);

        when(reserveDriverUseCase.reserveDriver(any(ReserveDriverCommand.class)))
                .thenThrow(new DriverNotAvailableException("Driver is not AVAILABLE"));

        mockMvc.perform(post("/api/v1/drivers/" + driverId + "/reserve")
                        .header("Idempotency-Key", "idemp-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DRIVER_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("POST /api/v1/drivers/{id}/reserve should return 409 when idempotency key conflicts with different payload")
    void shouldReturn409WhenIdempotencyKeyConflicts() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        ReserveDriverRequest request = new ReserveDriverRequest(tripId, 30);

        when(reserveDriverUseCase.reserveDriver(any(ReserveDriverCommand.class)))
                .thenThrow(new DuplicateResourceException("Idempotency-Key already used for a different reservation"));

        mockMvc.perform(post("/api/v1/drivers/" + driverId + "/reserve")
                        .header("Idempotency-Key", "idemp-key-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    // =========================================================================
    // POST /api/v1/drivers/{id}/release & GET /api/v1/drivers/available tests
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/drivers/{id}/release should return 200 when driver is released")
    void shouldReturn200WhenReleaseDriverSuccessfully() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        Instant releasedAt = Instant.now();

        ReleaseDriverResult result = new ReleaseDriverResult(driverId, tripId, DriverStatus.AVAILABLE, releasedAt);
        when(releaseDriverUseCase.releaseDriver(any(ReleaseDriverCommand.class))).thenReturn(result);

        ReleaseDriverRequest request = new ReleaseDriverRequest(tripId, "ASSIGN_FAILED");

        mockMvc.perform(post("/api/v1/drivers/" + driverId + "/release")
                        .header("Idempotency-Key", "rel-key-1")
                        .header("X-Correlation-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.driverId").value(driverId.toString()))
                .andExpect(jsonPath("$.data.tripId").value(tripId.toString()))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("GET /api/v1/drivers/available should return 200 with list of available drivers")
    void shouldReturn200WithAvailableDriversList() throws Exception {
        UUID driverId = UUID.randomUUID();
        AvailableDriverResult item = new AvailableDriverResult(
                driverId, DriverStatus.AVAILABLE, new BigDecimal("10.762622"), new BigDecimal("106.660172"), Instant.now());

        when(getAvailableDriversUseCase.getAvailableDrivers(10)).thenReturn(java.util.List.of(item));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/drivers/available")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].driverId").value(driverId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data[0].latitude").value(10.762622));
    }
}
