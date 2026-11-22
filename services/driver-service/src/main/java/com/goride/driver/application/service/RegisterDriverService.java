package com.goride.driver.application.service;

import com.goride.common.exception.DuplicateResourceException;
import com.goride.driver.application.port.in.DriverResult;
import com.goride.driver.application.port.in.RegisterDriverCommand;
import com.goride.driver.application.port.in.RegisterDriverUseCase;
import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.Vehicle;
import com.goride.driver.domain.model.VehicleStatus;
import com.goride.driver.domain.repository.DriverRepository;
import com.goride.driver.domain.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class RegisterDriverService implements RegisterDriverUseCase {

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;

    public RegisterDriverService(DriverRepository driverRepository, VehicleRepository vehicleRepository) {
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    @Transactional
    public DriverResult registerDriver(RegisterDriverCommand command) {
        UUID userId = Objects.requireNonNull(command.userId(), "userId must not be null");

        if (driverRepository.existsByUserId(userId)) {
            throw new DuplicateResourceException("userId", userId);
        }

        if (driverRepository.existsByLicenseNumber(command.licenseNumber())) {
            throw new DuplicateResourceException("licenseNumber", command.licenseNumber());
        }

        if (vehicleRepository.existsByPlateNumber(command.vehiclePlate())) {
            throw new DuplicateResourceException("vehiclePlate", command.vehiclePlate());
        }

        // 1. Create and persist Driver
        Driver driver = Driver.createNew(userId, command.licenseNumber());
        Driver savedDriver = driverRepository.save(driver);

        // 2. Create and persist Vehicle
        Instant now = Instant.now();
        Vehicle vehicle = new Vehicle(
                UUID.randomUUID(),
                savedDriver.getId(),
                command.vehiclePlate(),
                null,
                command.vehicleModel(),
                null,
                4,
                VehicleStatus.ACTIVE,
                now,
                now
        );
        Vehicle savedVehicle = vehicleRepository.save(vehicle);

        return new DriverResult(savedDriver, savedVehicle);
    }
}
