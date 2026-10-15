package com.goride.driver.domain.repository;

import com.goride.driver.domain.model.DriverLocation;

import java.util.Optional;
import java.util.UUID;

public interface DriverLocationRepository {

    Optional<DriverLocation> findByDriverId(UUID driverId);

    DriverLocation save(DriverLocation driverLocation);
}
