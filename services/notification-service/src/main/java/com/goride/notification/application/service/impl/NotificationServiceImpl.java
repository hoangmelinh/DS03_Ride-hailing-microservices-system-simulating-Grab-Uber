package com.goride.notification.application.service.impl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.exception.InvalidRequestException;
import com.goride.notification.application.dto.request.SendNotificationRequest;
import com.goride.notification.application.dto.response.NotificationResponse;
import com.goride.notification.application.service.NotificationService;
import com.goride.notification.domain.model.NotificationChannel;
import com.goride.notification.domain.model.NotificationStatus;
import com.goride.notification.infrastructure.persistence.entity.NotificationEntity;
import com.goride.notification.infrastructure.persistence.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Production-ready implementation of NotificationService.
 * Handles payload serialization, entity lifecycle, and clean query operations.
 */
@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public NotificationServiceImpl(NotificationRepository notificationRepository, ObjectMapper objectMapper) {
        this.notificationRepository = Objects.requireNonNull(notificationRepository, "notificationRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public NotificationResponse sendNotification(SendNotificationRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        log.info("Sending notification: userId={}, title='{}', type={}",
                request.getUserId(), request.getTitle(), request.getType());

        // 1. Serialize title & content into JSONB payload
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("title", request.getTitle());
        payloadMap.put("content", request.getContent());
        if (request.getTripId() != null) {
            payloadMap.put("tripId", request.getTripId().toString());
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification payload for user: {}", request.getUserId(), e);
            throw new InvalidRequestException("Failed to serialize notification payload: " + e.getMessage());
        }

        // 2. Prepare NotificationEntity
        Instant now = Instant.now();
        NotificationChannel channel = request.getChannel() != null ? request.getChannel() : NotificationChannel.SIMULATED;

        NotificationEntity entity = new NotificationEntity();
        entity.setSourceEventId(UUID.randomUUID());
        entity.setRecipientUserId(request.getUserId());
        entity.setRecipientRole(request.getRecipientRole() != null ? request.getRecipientRole() : "PASSENGER");
        entity.setTripId(request.getTripId());
        entity.setType(request.getType());
        entity.setChannel(channel);
        entity.setStatus(NotificationStatus.SENT);
        entity.setPayload(payloadJson);
        entity.setAttemptCount(1);
        entity.setSentAt(now);

        NotificationEntity saved = notificationRepository.save(entity);

        log.info("Notification sent successfully: id={}, userId={}, type={}, channel={}",
                saved.getId(), saved.getRecipientUserId(), saved.getType(), saved.getChannel());

        return NotificationResponse.from(saved, objectMapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsByUserId(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        log.debug("Fetching all notifications for userId={}", userId);

        List<NotificationEntity> entities = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId);
        return entities.stream()
                .map(entity -> NotificationResponse.from(entity, objectMapper))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotificationsByUserId(UUID userId, Pageable pageable) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(pageable, "pageable must not be null");
        log.debug("Fetching paged notifications for userId={}, page={}", userId, pageable.getPageNumber());

        Page<NotificationEntity> entityPage = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId, pageable);
        return entityPage.map(entity -> NotificationResponse.from(entity, objectMapper));
    }
}

