package com.goride.driver.application.service;

import com.goride.common.exception.EntityNotFoundException;
import com.goride.common.exception.InvalidRequestException;
import com.goride.driver.application.port.in.UpdateDriverStatusCommand;
import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.DriverStatus;
import com.goride.driver.domain.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateDriverStatusServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @InjectMocks
    private UpdateDriverStatusService service;

    private Driver driver;
    private UUID driverId;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        driver = new Driver(
                driverId,
                UUID.randomUUID(),
                "GPLX123456",
                DriverStatus.OFFLINE,
                null,
                null,
                0L,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should change driver status to AVAILABLE when status is ONLINE")
    void shouldChangeStatusToOnline() {
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverRepository.save(any(Driver.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateDriverStatusCommand command = new UpdateDriverStatusCommand(driverId, "ONLINE");
        Driver result = service.updateDriverStatus(command);

        assertThat(result.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        verify(driverRepository).save(driver);
    }

    @Test
    @DisplayName("Should change driver status to OFFLINE when status is OFFLINE")
    void shouldChangeStatusToOffline() {
        driver.goOnline(); // first go online
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverRepository.save(any(Driver.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateDriverStatusCommand command = new UpdateDriverStatusCommand(driverId, "OFFLINE");
        Driver result = service.updateDriverStatus(command);

        assertThat(result.getStatus()).isEqualTo(DriverStatus.OFFLINE);
        verify(driverRepository).save(driver);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when driver not found")
    void shouldThrowNotFoundWhenDriverMissing() {
        when(driverRepository.findById(driverId)).thenReturn(Optional.empty());

        UpdateDriverStatusCommand command = new UpdateDriverStatusCommand(driverId, "ONLINE");

        assertThatThrownBy(() -> service.updateDriverStatus(command))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Driver");

        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw InvalidRequestException when status is not ONLINE or OFFLINE")
    void shouldThrowInvalidRequestWhenUnknownStatus() {
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));

        UpdateDriverStatusCommand command = new UpdateDriverStatusCommand(driverId, "BUSY");

        assertThatThrownBy(() -> service.updateDriverStatus(command))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid status: 'BUSY'");

        verify(driverRepository, never()).save(any());
    }
}
