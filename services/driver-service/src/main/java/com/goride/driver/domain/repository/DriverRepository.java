package com.goride.driver.domain.repository;

import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.DriverStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain Repository interface for Driver aggregate.
 * Pure Java interface, independent of Spring Data or Hibernate.
 */
public interface DriverRepository {

    Optional<Driver> findById(UUID id);

    Optional<Driver> findByUserId(UUID userId);

    List<Driver> findByStatus(DriverStatus status);

    Driver save(Driver driver);

    /**
     * Executes atomic conditional update at the database level:
     * UPDATE drivers SET status = 'RESERVED', current_trip_id = :tripId, reserved_until = :expiresAt, version = version + 1
     * WHERE id = :driverId AND status = 'AVAILABLE';
     *
     * @return true if 1 row was updated (atomic reservation successful), false if driver is not AVAILABLE
     */
    boolean atomicReserve(UUID driverId, UUID tripId, Instant expiresAt);

    /**
     * Executes atomic conditional release:
     * UPDATE drivers SET status = 'AVAILABLE', current_trip_id = NULL, reserved_until = NULL, version = version + 1
     * WHERE id = :driverId AND status = 'RESERVED' AND current_trip_id = :tripId;
     */
    boolean atomicRelease(UUID driverId, UUID tripId);
}
