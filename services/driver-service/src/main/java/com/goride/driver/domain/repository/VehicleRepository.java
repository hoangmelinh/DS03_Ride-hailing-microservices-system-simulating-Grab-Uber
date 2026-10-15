package com.goride.driver.domain.repository;

import com.goride.driver.domain.model.Vehicle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository {

    Optional<Vehicle> findById(UUID id);

    Optional<Vehicle> findByPlateNumber(String plateNumber);

    List<Vehicle> findByDriverId(UUID driverId);

    Vehicle save(Vehicle vehicle);
}
