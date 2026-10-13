package com.goride.notification.infrastructure.persistence.repository;

import com.goride.notification.domain.model.DlqMessageStatus;
import com.goride.notification.infrastructure.persistence.entity.DeadLetterMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationDeadLetterRepository extends JpaRepository<DeadLetterMessageEntity, UUID> {

    Page<DeadLetterMessageEntity> findByStatus(DlqMessageStatus status, Pageable pageable);

    Page<DeadLetterMessageEntity> findByQueueNameAndStatus(String queueName, DlqMessageStatus status, Pageable pageable);

    List<DeadLetterMessageEntity> findByCorrelationId(UUID correlationId);

    long countByStatus(DlqMessageStatus status);
}
