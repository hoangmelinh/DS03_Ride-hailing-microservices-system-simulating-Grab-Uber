package com.goride.trip.infrastructure.persistence.repository;

import com.goride.trip.infrastructure.persistence.entity.IdempotencyRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, String> {

    Optional<IdempotencyRecordEntity> findByIdempotencyKey(String idempotencyKey);
}
