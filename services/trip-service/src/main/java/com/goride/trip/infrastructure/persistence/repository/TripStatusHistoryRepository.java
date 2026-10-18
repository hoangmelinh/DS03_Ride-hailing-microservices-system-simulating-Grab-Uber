package com.goride.trip.infrastructure.persistence.repository;

import com.goride.trip.infrastructure.persistence.entity.TripStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripStatusHistoryRepository extends JpaRepository<TripStatusHistoryEntity, UUID> {

    List<TripStatusHistoryEntity> findByTripIdOrderByChangedAtAsc(UUID tripId);
}
