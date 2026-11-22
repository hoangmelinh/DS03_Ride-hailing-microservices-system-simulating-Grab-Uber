package com.goride.driver.application.service;

import com.goride.common.exception.EntityNotFoundException;
import com.goride.driver.application.port.in.UpdateDriverLocationCommand;
import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.DriverLocation;
import com.goride.driver.domain.model.DriverStatus;
import com.goride.driver.domain.repository.DriverLocationRepository;
import com.goride.driver.domain.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateDriverLocationServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private DriverLocationRepository driverLocationRepository;

    @InjectMocks
    private UpdateDriverLocationService service;

    private UUID driverId;
    private Driver driver;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        driver = new Driver(
                driverId,
                UUID.randomUUID(),
                "GPLX-LOC-123",
                DriverStatus.AVAILABLE,
                null,
                null,
                0L,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should update coordinates when driver location already exists")
    void shouldUpdateCoordinatesWhenLocationExists() {
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));

        DriverLocation existingLocation = new DriverLocation(
                driverId,
                new BigDecimal("10.700000"),
                new BigDecimal("106.600000"),
                Instant.now().minusSeconds(60)
        );
        when(driverLocationRepository.findByDriverId(driverId)).thenReturn(Optional.of(existingLocation));
        when(driverLocationRepository.save(any(DriverLocation.class))).thenAnswer(inv -> inv.getArgument(0));

        BigDecimal newLat = new BigDecimal("10.762622");
        BigDecimal newLng = new BigDecimal("106.660172");
        UpdateDriverLocationCommand command = new UpdateDriverLocationCommand(driverId, newLat, newLng);

        DriverLocation result = service.updateLocation(command);

        assertThat(result.getDriverId()).isEqualTo(driverId);
        assertThat(result.getLatitude()).isEqualTo(newLat);
        assertThat(result.getLongitude()).isEqualTo(newLng);
        verify(driverLocationRepository).save(existingLocation);
    }

    @Test
    @DisplayName("Should create new driver location when record does not exist")
    void shouldCreateNewLocationWhenNotExists() {
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverLocationRepository.findByDriverId(driverId)).thenReturn(Optional.empty());
        when(driverLocationRepository.save(any(DriverLocation.class))).thenAnswer(inv -> inv.getArgument(0));

        BigDecimal newLat = new BigDecimal("10.762622");
        BigDecimal newLng = new BigDecimal("106.660172");
        UpdateDriverLocationCommand command = new UpdateDriverLocationCommand(driverId, newLat, newLng);

        DriverLocation result = service.updateLocation(command);

        assertThat(result.getDriverId()).isEqualTo(driverId);
        assertThat(result.getLatitude()).isEqualTo(newLat);
        assertThat(result.getLongitude()).isEqualTo(newLng);
        verify(driverLocationRepository).save(any(DriverLocation.class));
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when driver does not exist")
    void shouldThrowNotFoundWhenDriverMissing() {
        when(driverRepository.findById(driverId)).thenReturn(Optional.empty());

        UpdateDriverLocationCommand command = new UpdateDriverLocationCommand(
                driverId,
                new BigDecimal("10.762622"),
                new BigDecimal("106.660172")
        );

        assertThatThrownBy(() -> service.updateLocation(command))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Driver");

        verify(driverLocationRepository, never()).findByDriverId(any());
        verify(driverLocationRepository, never()).save(any());
    }
}
