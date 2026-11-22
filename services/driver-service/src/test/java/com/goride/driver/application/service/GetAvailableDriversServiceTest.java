package com.goride.driver.application.service;

import com.goride.driver.application.port.in.AvailableDriverResult;
import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.DriverLocation;
import com.goride.driver.domain.model.DriverStatus;
import com.goride.driver.domain.repository.DriverLocationRepository;
import com.goride.driver.domain.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAvailableDriversServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private DriverLocationRepository driverLocationRepository;

    private GetAvailableDriversService service;

    @BeforeEach
    void setUp() {
        service = new GetAvailableDriversService(driverRepository, driverLocationRepository, 20, 100);
    }

    @Test
    @DisplayName("Should return available drivers enriched with location when present and respect limit")
    void getAvailableDrivers_ReturnsDriversWithLocationsAndLimit() {
        UUID d1 = UUID.randomUUID();
        UUID d2 = UUID.randomUUID();
        UUID d3 = UUID.randomUUID();

        Driver driver1 = new Driver(d1, UUID.randomUUID(), "L1", DriverStatus.AVAILABLE, null, null, 0L, Instant.now(), Instant.now());
        Driver driver2 = new Driver(d2, UUID.randomUUID(), "L2", DriverStatus.AVAILABLE, null, null, 0L, Instant.now(), Instant.now());
        Driver driver3 = new Driver(d3, UUID.randomUUID(), "L3", DriverStatus.AVAILABLE, null, null, 0L, Instant.now(), Instant.now());

        when(driverRepository.findByStatus(DriverStatus.AVAILABLE)).thenReturn(List.of(driver1, driver2, driver3));
        when(driverLocationRepository.findByDriverIds(List.of(d1, d2)))
                .thenReturn(List.of(new DriverLocation(d1, new BigDecimal("10.762622"), new BigDecimal("106.660172"), Instant.now())));

        List<AvailableDriverResult> results = service.getAvailableDrivers(2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).driverId()).isEqualTo(d1);
        assertThat(results.get(0).latitude()).isEqualByComparingTo("10.762622");
        assertThat(results.get(1).driverId()).isEqualTo(d2);
        assertThat(results.get(1).latitude()).isNull();
    }
}
