package com.goride.notification.infrastructure.persistence.repository;

import com.goride.notification.domain.model.NotificationStatus;
import com.goride.notification.infrastructure.persistence.entity.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    Optional<NotificationEntity> findBySourceEventIdAndRecipientUserIdAndType(
        UUID sourceEventId,
        UUID recipientUserId,
        String type
    );

    boolean existsBySourceEventIdAndRecipientUserIdAndType(
        UUID sourceEventId,
        UUID recipientUserId,
        String type
    );

    Page<NotificationEntity> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);

    Page<NotificationEntity> findByRecipientUserIdAndRecipientRoleOrderByCreatedAtDesc(
        UUID recipientUserId,
        String recipientRole,
        Pageable pageable
    );

    Page<NotificationEntity> findByTripId(UUID tripId, Pageable pageable);

    Page<NotificationEntity> findByStatus(NotificationStatus status, Pageable pageable);
}
