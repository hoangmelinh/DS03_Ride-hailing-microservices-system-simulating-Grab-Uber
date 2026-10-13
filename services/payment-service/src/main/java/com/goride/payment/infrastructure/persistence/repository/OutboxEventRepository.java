package com.goride.payment.infrastructure.persistence.repository;

import com.goride.payment.domain.model.OutboxStatus;
import com.goride.payment.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findByStatus(OutboxStatus status);

    List<OutboxEventEntity> findByStatus(OutboxStatus status, Pageable pageable);

    List<OutboxEventEntity> findByStatusOrderByOccurredAtAsc(OutboxStatus status);

    @Query("SELECT e FROM OutboxEventEntity e WHERE e.status = :status AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now) ORDER BY e.occurredAt ASC")
    List<OutboxEventEntity> findPendingEventsToPublish(@Param("status") OutboxStatus status, @Param("now") Instant now, Pageable pageable);
}
