package com.goride.driver.adapter.out.persistence.repository;

import com.goride.driver.adapter.out.persistence.entity.DriverReservationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverReservationSpringDataJpaRepository extends JpaRepository<DriverReservationJpaEntity, UUID> {

    Optional<DriverReservationJpaEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT r FROM DriverReservationJpaEntity r " +
           "WHERE r.driverId = :driverId AND r.status IN ('RESERVED', 'CONFIRMED')")
    Optional<DriverReservationJpaEntity> findActiveByDriverId(@Param("driverId") UUID driverId);

    @Query("SELECT r FROM DriverReservationJpaEntity r " +
           "WHERE r.tripId = :tripId AND r.status IN ('RESERVED', 'CONFIRMED')")
    Optional<DriverReservationJpaEntity> findActiveByTripId(@Param("tripId") UUID tripId);
}
