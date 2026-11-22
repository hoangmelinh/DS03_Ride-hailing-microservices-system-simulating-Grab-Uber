package com.goride.trip.infrastructure.persistence.repository;

import com.goride.trip.domain.model.TripStatus;
import com.goride.trip.infrastructure.persistence.entity.TripEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<TripEntity, UUID> {

    Page<TripEntity> findByPassengerId(UUID passengerId, Pageable pageable);

    Page<TripEntity> findByPassengerIdOrderByRequestedAtDesc(UUID passengerId, Pageable pageable);

    Page<TripEntity> findByDriverIdAndStatus(UUID driverId, TripStatus status, Pageable pageable);

    List<TripEntity> findByStatus(TripStatus status);

    Optional<TripEntity> findByIdAndPassengerId(UUID id, UUID passengerId);

    Optional<TripEntity> findByIdAndDriverId(UUID id, UUID driverId);
}
