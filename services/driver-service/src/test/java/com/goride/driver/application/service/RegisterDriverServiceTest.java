package com.goride.driver.application.service;

import com.goride.common.exception.DuplicateResourceException;
import com.goride.driver.application.port.in.DriverResult;
import com.goride.driver.application.port.in.RegisterDriverCommand;
import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.DriverStatus;
import com.goride.driver.domain.model.Vehicle;
import com.goride.driver.domain.repository.DriverRepository;
import com.goride.driver.domain.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterDriverServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private RegisterDriverService service;

    private UUID userId;
    private String licenseNumber;
    private String vehiclePlate;
    private String vehicleModel;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        licenseNumber = "GPLX-123456";
        vehiclePlate = "30A-99999";
        vehicleModel = "Toyota Vios 2023";
    }

    @Test
    @DisplayName("Should register driver successfully with valid userId and vehicle info")
    void shouldRegisterDriverSuccessfully() {
        when(driverRepository.existsByUserId(userId)).thenReturn(false);
        when(driverRepository.existsByLicenseNumber(licenseNumber)).thenReturn(false);
        when(vehicleRepository.existsByPlateNumber(vehiclePlate)).thenReturn(false);

        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterDriverCommand command = new RegisterDriverCommand(
                userId,
                licenseNumber,
                vehiclePlate,
                vehicleModel
        );

        DriverResult result = service.registerDriver(command);

        assertThat(result).isNotNull();
        assertThat(result.driver()).isNotNull();
        assertThat(result.driver().getUserId()).isEqualTo(userId);
        assertThat(result.driver().getLicenseNumber()).isEqualTo(licenseNumber);
        assertThat(result.driver().getStatus()).isEqualTo(DriverStatus.OFFLINE);

        assertThat(result.vehicle()).isNotNull();
        assertThat(result.vehicle().getPlateNumber()).isEqualTo(vehiclePlate);
        assertThat(result.vehicle().getModel()).isEqualTo(vehicleModel);

        verify(driverRepository).save(any(Driver.class));
        verify(vehicleRepository).save(any(Vehicle.class));
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when userId already exists")
    void shouldThrowWhenUserIdAlreadyExists() {
        when(driverRepository.existsByUserId(userId)).thenReturn(true);

        RegisterDriverCommand command = new RegisterDriverCommand(
                userId,
                licenseNumber,
                vehiclePlate,
                vehicleModel
        );

        assertThatThrownBy(() -> service.registerDriver(command))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("userId");

        verify(driverRepository, never()).save(any());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when licenseNumber already exists")
    void shouldThrowWhenLicenseNumberAlreadyExists() {
        when(driverRepository.existsByUserId(userId)).thenReturn(false);
        when(driverRepository.existsByLicenseNumber(licenseNumber)).thenReturn(true);

        RegisterDriverCommand command = new RegisterDriverCommand(
                userId,
                licenseNumber,
                vehiclePlate,
                vehicleModel
        );

        assertThatThrownBy(() -> service.registerDriver(command))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("licenseNumber");

        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when vehiclePlate already exists")
    void shouldThrowWhenVehiclePlateAlreadyExists() {
        when(driverRepository.existsByUserId(userId)).thenReturn(false);
        when(driverRepository.existsByLicenseNumber(licenseNumber)).thenReturn(false);
        when(vehicleRepository.existsByPlateNumber(vehiclePlate)).thenReturn(true);

        RegisterDriverCommand command = new RegisterDriverCommand(
                userId,
                licenseNumber,
                vehiclePlate,
                vehicleModel
        );

        assertThatThrownBy(() -> service.registerDriver(command))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("vehiclePlate");

        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw NullPointerException when command has null userId")
    void shouldThrowWhenUserIdIsNull() {
        assertThatThrownBy(() -> new RegisterDriverCommand(
                null,
                licenseNumber,
                vehiclePlate,
                vehicleModel
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("userId");
    }
}

