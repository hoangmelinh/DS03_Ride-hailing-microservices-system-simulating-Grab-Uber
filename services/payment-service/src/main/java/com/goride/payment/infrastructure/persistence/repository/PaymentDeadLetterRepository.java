package com.goride.payment.infrastructure.persistence.repository;

import com.goride.payment.domain.model.DlqMessageStatus;
import com.goride.payment.infrastructure.persistence.entity.DeadLetterMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentDeadLetterRepository extends JpaRepository<DeadLetterMessageEntity, UUID> {

    Page<DeadLetterMessageEntity> findByStatus(DlqMessageStatus status, Pageable pageable);

    Page<DeadLetterMessageEntity> findByQueueNameAndStatus(String queueName, DlqMessageStatus status, Pageable pageable);

    List<DeadLetterMessageEntity> findByCorrelationId(UUID correlationId);

    long countByStatus(DlqMessageStatus status);
}
