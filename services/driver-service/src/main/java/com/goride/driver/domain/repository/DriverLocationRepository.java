package com.goride.driver.domain.repository;

import com.goride.driver.domain.model.DriverLocation;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain Repository interface for DriverLocation entity.
 */
public interface DriverLocationRepository {

    Optional<DriverLocation> findByDriverId(UUID driverId);

    DriverLocation save(DriverLocation driverLocation);
}
