package com.goride.driver.domain.repository;

import com.goride.driver.domain.model.DriverLocation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverLocationRepository {

    Optional<DriverLocation> findByDriverId(UUID driverId);

    List<DriverLocation> findByDriverIds(Collection<UUID> driverIds);

    DriverLocation save(DriverLocation driverLocation);
}
