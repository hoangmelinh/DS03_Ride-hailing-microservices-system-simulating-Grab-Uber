package com.goride.trip.infrastructure.persistence.repository;

import com.goride.trip.infrastructure.persistence.entity.InboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InboxEventRepository extends JpaRepository<InboxEventEntity, UUID> {

    boolean existsByEventIdAndConsumer(UUID eventId, String consumer);
}
