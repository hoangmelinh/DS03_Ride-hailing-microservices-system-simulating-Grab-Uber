package com.goride.payment.infrastructure.persistence.repository;

import com.goride.payment.infrastructure.persistence.entity.InboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InboxEventRepository extends JpaRepository<InboxEventEntity, UUID> {

    boolean existsByEventIdAndConsumer(UUID eventId, String consumer);

    List<InboxEventEntity> findByConsumer(String consumer);

    List<InboxEventEntity> findByEventType(String eventType);
}
