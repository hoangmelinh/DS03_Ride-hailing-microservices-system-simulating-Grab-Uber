package com.goride.driver.adapter.out.persistence.repository;

import com.goride.driver.adapter.out.persistence.entity.DriverJpaEntity;
import com.goride.driver.domain.model.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverSpringDataJpaRepository extends JpaRepository<DriverJpaEntity, UUID> {

    Optional<DriverJpaEntity> findByUserId(UUID userId);

    Optional<DriverJpaEntity> findByLicenseNumber(String licenseNumber);

    List<DriverJpaEntity> findByStatus(DriverStatus status);

    /**
     * ATOMIC RESERVATION - Concurrency Control Invariant.
     * Guaranteed single winner at the database level.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DriverJpaEntity d " +
           "SET d.status = 'RESERVED', " +
           "    d.currentTripId = :tripId, " +
           "    d.reservedUntil = :expiresAt, " +
           "    d.version = d.version + 1, " +
           "    d.updatedAt = :now " +
           "WHERE d.id = :driverId AND d.status = 'AVAILABLE'")
    int atomicReserve(@Param("driverId") UUID driverId,
                      @Param("tripId") UUID tripId,
                      @Param("expiresAt") Instant expiresAt,
                      @Param("now") Instant now);

    /**
     * ATOMIC RELEASE - Saga Compensation.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DriverJpaEntity d " +
           "SET d.status = 'AVAILABLE', " +
           "    d.currentTripId = NULL, " +
           "    d.reservedUntil = NULL, " +
           "    d.version = d.version + 1, " +
           "    d.updatedAt = :now " +
           "WHERE d.id = :driverId AND d.status = 'RESERVED' AND d.currentTripId = :tripId")
    int atomicRelease(@Param("driverId") UUID driverId,
                      @Param("tripId") UUID tripId,
                      @Param("now") Instant now);
}
