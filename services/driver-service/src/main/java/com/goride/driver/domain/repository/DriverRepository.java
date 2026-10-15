package com.goride.driver.domain.repository;

import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.DriverStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverRepository {

    Optional<Driver> findById(UUID id);

    Optional<Driver> findByUserId(UUID userId);

    List<Driver> findByStatus(DriverStatus status);

    Driver save(Driver driver);

    boolean atomicReserve(UUID driverId, UUID tripId, Instant expiresAt);

    boolean atomicRelease(UUID driverId, UUID tripId);
}
