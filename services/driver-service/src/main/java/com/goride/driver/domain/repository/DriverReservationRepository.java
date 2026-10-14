package com.goride.driver.domain.repository;

import com.goride.driver.domain.model.DriverReservation;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain Repository interface for DriverReservation entity.
 */
public interface DriverReservationRepository {

    Optional<DriverReservation> findById(UUID id);

    Optional<DriverReservation> findByIdempotencyKey(String idempotencyKey);

    Optional<DriverReservation> findActiveByDriverId(UUID driverId);

    Optional<DriverReservation> findActiveByTripId(UUID tripId);

    DriverReservation save(DriverReservation reservation);
}
