package com.goride.payment.infrastructure.persistence.repository;

import com.goride.payment.domain.model.IdempotencyStatus;
import com.goride.payment.infrastructure.persistence.entity.IdempotencyRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, String> {

    Optional<IdempotencyRecordEntity> findByIdempotencyKeyAndOperation(String idempotencyKey, String operation);

    List<IdempotencyRecordEntity> findByStatus(IdempotencyStatus status);

    void deleteByExpiresAtBefore(Instant now);
}
