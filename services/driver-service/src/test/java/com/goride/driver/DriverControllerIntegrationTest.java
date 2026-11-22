package com.goride.driver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.driver.adapter.in.web.dto.RegisterDriverRequest;
import com.goride.driver.adapter.in.web.dto.ReserveDriverRequest;
import com.goride.driver.adapter.in.web.dto.UpdateDriverLocationRequest;
import com.goride.driver.adapter.in.web.dto.UpdateDriverStatusRequest;
import com.goride.driver.adapter.out.persistence.entity.OutboxEventJpaEntity;
import com.goride.driver.adapter.out.persistence.repository.OutboxEventSpringDataJpaRepository;
import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.DriverLocation;
import com.goride.driver.domain.model.DriverReservation;
import com.goride.driver.domain.model.DriverStatus;
import com.goride.driver.domain.model.ReservationStatus;
import com.goride.driver.domain.model.Vehicle;
import com.goride.driver.domain.repository.DriverLocationRepository;
import com.goride.driver.domain.repository.DriverRepository;
import com.goride.driver.domain.repository.DriverReservationRepository;
import com.goride.driver.domain.repository.VehicleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(PostgresAvailableCondition.class)
class DriverControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private DriverLocationRepository driverLocationRepository;

    @Autowired
    private DriverReservationRepository driverReservationRepository;

    @Autowired
    private OutboxEventSpringDataJpaRepository outboxEventSpringDataJpaRepository;

    @Test
    @DisplayName("POST /api/v1/drivers should register new driver and vehicle successfully in PostgreSQL")
    void shouldRegisterDriverSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String licenseNumber = "GPLX-" + uniqueSuffix;
        String plateNumber = "30A-" + uniqueSuffix;

        RegisterDriverRequest request = new RegisterDriverRequest(
                userId,
                licenseNumber,
                plateNumber,
                "Toyota Vios 2023"
        );

        mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.licenseNumber").value(licenseNumber))
                .andExpect(jsonPath("$.data.status").value("OFFLINE"))
                .andExpect(jsonPath("$.data.vehicle.plateNumber").value(plateNumber))
                .andExpect(jsonPath("$.data.vehicle.model").value("Toyota Vios 2023"))
                .andExpect(jsonPath("$.data.vehicle.status").value("ACTIVE"));

        // Verify in database via repository
        Optional<Driver> savedDriver = driverRepository.findByLicenseNumber(licenseNumber);
        assertThat(savedDriver).isPresent();
        assertThat(savedDriver.get().getUserId()).isEqualTo(userId);
        assertThat(savedDriver.get().getStatus().name()).isEqualTo("OFFLINE");

        Optional<Vehicle> savedVehicle = vehicleRepository.findByPlateNumber(plateNumber);
        assertThat(savedVehicle).isPresent();
        assertThat(savedVehicle.get().getDriverId()).isEqualTo(savedDriver.get().getId());
        assertThat(savedVehicle.get().getModel()).isEqualTo("Toyota Vios 2023");
    }

    @Test
    @DisplayName("POST /api/v1/drivers should return 400 when missing userId")
    void shouldReturn400WhenMissingUserId() throws Exception {
        RegisterDriverRequest invalidRequest = new RegisterDriverRequest(
                null,
                "GPLX-NOUSER",
                "29A-12345",
                "Honda Civic"
        );

        mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/drivers should return 400 when missing required fields")
    void shouldReturn400WhenMissingRequiredFields() throws Exception {
        RegisterDriverRequest invalidRequest = new RegisterDriverRequest(
                UUID.randomUUID(),
                "",
                "",
                ""
        );

        mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/drivers should return 409 when userId is duplicated")
    void shouldReturn409WhenDuplicateUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        String uniqueSuffix1 = UUID.randomUUID().toString().substring(0, 8);
        String uniqueSuffix2 = UUID.randomUUID().toString().substring(0, 8);

        RegisterDriverRequest request1 = new RegisterDriverRequest(
                userId,
                "GPLX-USER1-" + uniqueSuffix1,
                "51F-" + uniqueSuffix1,
                "Hyundai Accent"
        );

        mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Second request with same userId but different license & plate
        RegisterDriverRequest request2 = new RegisterDriverRequest(
                userId,
                "GPLX-USER2-" + uniqueSuffix2,
                "51G-" + uniqueSuffix2,
                "Kia Morning"
        );

        mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("POST /api/v1/drivers should return 409 when license number is duplicated")
    void shouldReturn409WhenDuplicateLicenseNumber() throws Exception {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String licenseNumber = "GPLX-DUP-" + uniqueSuffix;

        RegisterDriverRequest request1 = new RegisterDriverRequest(
                userId1,
                licenseNumber,
                "51F-" + uniqueSuffix,
                "Hyundai Accent"
        );

        mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Second request with same license number
        RegisterDriverRequest request2 = new RegisterDriverRequest(
                userId2,
                licenseNumber,
                "51G-" + uniqueSuffix,
                "Kia Morning"
        );

        mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("POST /api/v1/drivers should return 409 when vehicle plate is duplicated")
    void shouldReturn409WhenDuplicateVehiclePlate() throws Exception {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String plateNumber = "51H-" + uniqueSuffix;

        RegisterDriverRequest request1 = new RegisterDriverRequest(
                userId1,
                "GPLX-P1-" + uniqueSuffix,
                plateNumber,
                "Hyundai Accent"
        );

        mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Second request with same vehicle plate
        RegisterDriverRequest request2 = new RegisterDriverRequest(
                userId2,
                "GPLX-P2-" + uniqueSuffix,
                plateNumber,
                "Kia Morning"
        );

        mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("PATCH /api/v1/drivers/{id}/status should change status to ONLINE (AVAILABLE) successfully")
    void shouldChangeStatusToOnlineSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String licenseNumber = "GPLX-STAT-" + uniqueSuffix;
        String plateNumber = "29A-" + uniqueSuffix;

        RegisterDriverRequest request = new RegisterDriverRequest(
                userId,
                licenseNumber,
                plateNumber,
                "Honda City"
        );

        String createResponse = mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String driverId = objectMapper.readTree(createResponse).path("data").path("id").asText();

        // Change status to ONLINE
        UpdateDriverStatusRequest onlineRequest = new UpdateDriverStatusRequest("ONLINE");
        mockMvc.perform(patch("/api/v1/drivers/" + driverId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(onlineRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(driverId))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));

        // Verify in DB
        Driver updatedDriver = driverRepository.findById(UUID.fromString(driverId)).orElseThrow();
        assertThat(updatedDriver.getStatus().name()).isEqualTo("AVAILABLE");

        // Change status back to OFFLINE
        UpdateDriverStatusRequest offlineRequest = new UpdateDriverStatusRequest("OFFLINE");
        mockMvc.perform(patch("/api/v1/drivers/" + driverId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(offlineRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("OFFLINE"));

        updatedDriver = driverRepository.findById(UUID.fromString(driverId)).orElseThrow();
        assertThat(updatedDriver.getStatus().name()).isEqualTo("OFFLINE");
    }

    @Test
    @DisplayName("PATCH /api/v1/drivers/{id}/status should return 404 when driver not found")
    void shouldReturn404WhenDriverNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        UpdateDriverStatusRequest request = new UpdateDriverStatusRequest("ONLINE");

        mockMvc.perform(patch("/api/v1/drivers/" + nonExistentId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/v1/drivers/{id}/status should return 400 when status is invalid")
    void shouldReturn400WhenStatusIsInvalid() throws Exception {
        UUID randomId = UUID.randomUUID();
        UpdateDriverStatusRequest request = new UpdateDriverStatusRequest("UNKNOWN_STATUS");

        mockMvc.perform(patch("/api/v1/drivers/" + randomId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("PUT /api/v1/drivers/{id}/location should update location in driver_locations table successfully")
    void shouldUpdateDriverLocationSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String licenseNumber = "GPLX-GPS-" + uniqueSuffix;
        String plateNumber = "59A-" + uniqueSuffix;

        RegisterDriverRequest request = new RegisterDriverRequest(
                userId,
                licenseNumber,
                plateNumber,
                "Mazda 3"
        );

        String createResponse = mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String driverId = objectMapper.readTree(createResponse).path("data").path("id").asText();

        // Update GPS location first time
        UpdateDriverLocationRequest locRequest = new UpdateDriverLocationRequest(
                new BigDecimal("10.762622"),
                new BigDecimal("106.660172")
        );

        mockMvc.perform(put("/api/v1/drivers/" + driverId + "/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.driverId").value(driverId))
                .andExpect(jsonPath("$.data.latitude").value(10.762622))
                .andExpect(jsonPath("$.data.longitude").value(106.660172))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        // Verify in database
        Optional<DriverLocation> savedLoc = driverLocationRepository.findByDriverId(UUID.fromString(driverId));
        assertThat(savedLoc).isPresent();
        assertThat(savedLoc.get().getLatitude().stripTrailingZeros()).isEqualTo(new BigDecimal("10.762622").stripTrailingZeros());
        assertThat(savedLoc.get().getLongitude().stripTrailingZeros()).isEqualTo(new BigDecimal("106.660172").stripTrailingZeros());

        // Update GPS location second time (coordinates changed)
        UpdateDriverLocationRequest updateLocRequest = new UpdateDriverLocationRequest(
                new BigDecimal("10.778899"),
                new BigDecimal("106.699999")
        );

        mockMvc.perform(put("/api/v1/drivers/" + driverId + "/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateLocRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.latitude").value(10.778899))
                .andExpect(jsonPath("$.data.longitude").value(106.699999));

        savedLoc = driverLocationRepository.findByDriverId(UUID.fromString(driverId));
        assertThat(savedLoc).isPresent();
        assertThat(savedLoc.get().getLatitude().stripTrailingZeros()).isEqualTo(new BigDecimal("10.778899").stripTrailingZeros());
    }

    // =========================================================================
    // POST /api/v1/drivers/{id}/reserve Integration & Concurrency Tests
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/drivers/{id}/reserve should reserve driver, create DB records, outbox event and support idempotent replay")
    void shouldReserveDriverSuccessfullyAndIdempotentlyInPostgreSQL() throws Exception {
        // 1. Register driver
        UUID userId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterDriverRequest regRequest = new RegisterDriverRequest(
                userId, "GPLX-" + suffix, "30A-" + suffix, "Toyota Vios"
        );
        String regResponseStr = mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String driverId = objectMapper.readTree(regResponseStr).path("data").path("id").asText();

        // 2. Set driver status to ONLINE/AVAILABLE
        UpdateDriverStatusRequest statusRequest = new UpdateDriverStatusRequest("ONLINE");
        mockMvc.perform(patch("/api/v1/drivers/" + driverId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk());

        // 3. Reserve driver
        UUID tripId = UUID.randomUUID();
        String idempKey = "idemp-res-" + UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        ReserveDriverRequest reserveRequest = new ReserveDriverRequest(tripId, 30);

        String reserveResponseStr = mockMvc.perform(post("/api/v1/drivers/" + driverId + "/reserve")
                        .header("Idempotency-Key", idempKey)
                        .header("X-Correlation-Id", correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reserveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.driverId").value(driverId))
                .andExpect(jsonPath("$.data.tripId").value(tripId.toString()))
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String reservationId = objectMapper.readTree(reserveResponseStr).path("data").path("reservationId").asText();

        // 4. Verify Driver in PostgreSQL
        Driver updatedDriver = driverRepository.findById(UUID.fromString(driverId)).orElseThrow();
        assertThat(updatedDriver.getStatus()).isEqualTo(DriverStatus.RESERVED);
        assertThat(updatedDriver.getCurrentTripId()).isEqualTo(tripId);
        assertThat(updatedDriver.getReservedUntil()).isNotNull();

        // 5. Verify DriverReservation in PostgreSQL
        Optional<DriverReservation> savedRes = driverReservationRepository.findById(UUID.fromString(reservationId));
        assertThat(savedRes).isPresent();
        assertThat(savedRes.get().getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(savedRes.get().getDriverId()).isEqualTo(UUID.fromString(driverId));
        assertThat(savedRes.get().getTripId()).isEqualTo(tripId);
        assertThat(savedRes.get().getIdempotencyKey()).isEqualTo(idempKey);

        // 6. Verify Outbox Event in PostgreSQL
        List<OutboxEventJpaEntity> pendingEvents = outboxEventSpringDataJpaRepository.findAll();
        boolean foundOutboxEvent = pendingEvents.stream().anyMatch(e ->
                e.getAggregateType().equals("DRIVER") &&
                e.getAggregateId().equals(UUID.fromString(driverId)) &&
                e.getEventType().equals("driver.reserved") &&
                e.getCorrelationId().equals(UUID.fromString(correlationId)) &&
                e.getPayload().contains(tripId.toString())
        );
        assertThat(foundOutboxEvent).isTrue();

        // 7. Idempotent replay: send exact same request again
        mockMvc.perform(post("/api/v1/drivers/" + driverId + "/reserve")
                        .header("Idempotency-Key", idempKey)
                        .header("X-Correlation-Id", correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reserveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reservationId").value(reservationId))
                .andExpect(jsonPath("$.data.status").value("RESERVED"));
    }

    @Test
    @DisplayName("CONCURRENCY: Two simultaneous reservation requests for the same driver must result in exactly 1 success (200) and 1 failure (409)")
    void shouldEnforceAtomicReservationUnderConcurrencyInPostgreSQL() throws Exception {
        // 1. Register driver and set to AVAILABLE
        UUID userId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterDriverRequest regRequest = new RegisterDriverRequest(
                userId, "GPLX-" + suffix, "30A-" + suffix, "Honda City"
        );
        String regResponseStr = mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String driverId = objectMapper.readTree(regResponseStr).path("data").path("id").asText();

        mockMvc.perform(patch("/api/v1/drivers/" + driverId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateDriverStatusRequest("ONLINE"))))
                .andExpect(status().isOk());

        // 2. Prepare 2 concurrent requests for different trips and different idempotency keys
        UUID trip1 = UUID.randomUUID();
        UUID trip2 = UUID.randomUUID();
        String key1 = "concurrent-idemp-1-" + UUID.randomUUID();
        String key2 = "concurrent-idemp-2-" + UUID.randomUUID();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        // Worker 1
        futures.add(executor.submit(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();
                int status = mockMvc.perform(post("/api/v1/drivers/" + driverId + "/reserve")
                                .header("Idempotency-Key", key1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new ReserveDriverRequest(trip1, 30))))
                        .andReturn().getResponse().getStatus();
                if (status == 200) successCount.incrementAndGet();
                else if (status == 409) conflictCount.incrementAndGet();
            } catch (Exception e) {
                // handle
            }
        }));

        // Worker 2
        futures.add(executor.submit(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();
                int status = mockMvc.perform(post("/api/v1/drivers/" + driverId + "/reserve")
                                .header("Idempotency-Key", key2)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new ReserveDriverRequest(trip2, 30))))
                        .andReturn().getResponse().getStatus();
                if (status == 200) successCount.incrementAndGet();
                else if (status == 409) conflictCount.incrementAndGet();
            } catch (Exception e) {
                // handle
            }
        }));

        // Fire both threads simultaneously
        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // 3. Verify exactly 1 winner (200) and 1 loser (409)
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        // 4. Verify DB state: Driver is RESERVED with exactly one of the trips
        Driver driver = driverRepository.findById(UUID.fromString(driverId)).orElseThrow();
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.RESERVED);
        assertThat(driver.getCurrentTripId()).isIn(trip1, trip2);

        // 5. Verify driver_reservations: exactly 1 active reservation for this driver
        Optional<DriverReservation> activeRes = driverReservationRepository.findActiveByDriverId(UUID.fromString(driverId));
        assertThat(activeRes).isPresent();
        assertThat(activeRes.get().getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(activeRes.get().getTripId()).isEqualTo(driver.getCurrentTripId());
    }
}
