package com.goride.trip.infrastructure.persistence.repository;

import com.goride.trip.domain.model.DlqMessageStatus;
import com.goride.trip.infrastructure.persistence.entity.DeadLetterMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripDeadLetterRepository extends JpaRepository<DeadLetterMessageEntity, UUID> {

    List<DeadLetterMessageEntity> findByStatus(DlqMessageStatus status);

    List<DeadLetterMessageEntity> findByQueueNameAndStatus(String queueName, DlqMessageStatus status);
}
