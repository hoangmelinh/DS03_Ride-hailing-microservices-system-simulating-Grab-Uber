package com.goride.user.infrastructure.persistence.repository;

import com.goride.user.domain.model.OutboxStatus;
import com.goride.user.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findByStatusAndNextRetryAtBefore(OutboxStatus status, Instant timestamp);

    List<OutboxEventEntity> findTop50ByStatusOrderByOccurredAtAsc(OutboxStatus status);
}
