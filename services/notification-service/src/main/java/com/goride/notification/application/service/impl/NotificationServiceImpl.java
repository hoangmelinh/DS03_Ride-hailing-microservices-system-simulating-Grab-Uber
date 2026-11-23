package com.goride.notification.application.service.impl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.exception.InvalidRequestException;
import com.goride.notification.application.dto.request.SendNotificationRequest;
import com.goride.notification.application.dto.response.NotificationResponse;
import com.goride.notification.application.service.NotificationService;
import com.goride.notification.domain.model.NotificationChannel;
import com.goride.notification.domain.model.NotificationStatus;
import com.goride.notification.infrastructure.persistence.entity.InboxEventEntity;
import com.goride.notification.infrastructure.persistence.entity.NotificationEntity;
import com.goride.notification.infrastructure.persistence.repository.NotificationInboxEventRepository;
import com.goride.notification.infrastructure.persistence.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Production-ready implementation of NotificationService.
 * Handles payload serialization, entity lifecycle, inbox deduplication and asynchronous event consumption.
 */
@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final NotificationInboxEventRepository inboxEventRepository;
    private final ObjectMapper objectMapper;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                  NotificationInboxEventRepository inboxEventRepository,
                                  ObjectMapper objectMapper) {
        this.notificationRepository = Objects.requireNonNull(notificationRepository, "notificationRepository must not be null");
        this.inboxEventRepository = Objects.requireNonNull(inboxEventRepository, "inboxEventRepository must not be null");
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
        // Explicitly maintain invariant: sentAt >= createdAt
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setSentAt(now);

        NotificationEntity saved = notificationRepository.save(entity);

        // Register commit-confirmed success log only when both an actual transaction and synchronization are active
        final UUID notificationId = saved.getId();
        final UUID recipientUserId = saved.getRecipientUserId();
        final String notificationType = saved.getType();
        final NotificationChannel notificationChannel = saved.getChannel();
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("Notification sent successfully: id={}, userId={}, type={}, channel={}",
                            notificationId, recipientUserId, notificationType, notificationChannel);
                }
            });
        } else {
            log.debug("Skipped commit-confirmed notification success log because active transaction synchronization is unavailable: id={}, userId={}",
                    notificationId, recipientUserId);
        }

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

    @Override
    public NotificationResponse processEventNotification(UUID eventId, String eventType, UUID correlationId, JsonNode payloadNode) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");

        log.info("Processing asynchronous notification for eventId={}, type={}, correlationId={}",
                eventId, eventType, correlationId);

        // 1. Inbox Deduplication: Skip if already processed by notification-service
        if (inboxEventRepository.existsByEventIdAndConsumer(eventId, "notification-service")) {
            log.info("Duplicate eventId={} for consumer 'notification-service'. Skipping.", eventId);
            return null;
        }

        // 2. Extract recipient, tripId, title and content from payload
        UUID recipientUserId = null;
        String recipientRole = "PASSENGER";
        UUID tripId = null;

        if (payloadNode != null) {
            if (payloadNode.hasNonNull("passengerId")) {
                try {
                    recipientUserId = UUID.fromString(payloadNode.get("passengerId").asText());
                } catch (IllegalArgumentException ignored) {}
            }
            if (recipientUserId == null && payloadNode.hasNonNull("userId")) {
                try {
                    recipientUserId = UUID.fromString(payloadNode.get("userId").asText());
                } catch (IllegalArgumentException ignored) {}
            }
            if (payloadNode.hasNonNull("tripId")) {
                try {
                    tripId = UUID.fromString(payloadNode.get("tripId").asText());
                } catch (IllegalArgumentException ignored) {}
            }
        }

        if (recipientUserId == null) {
            recipientUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        }

        String title;
        String content;

        switch (eventType) {
            case "trip.created" -> {
                title = "Đang tìm tài xế";
                content = "Yêu cầu đặt chuyến thành công. Hệ thống đang tìm tài xế gần bạn nhất.";
            }
            case "driver.reserved" -> {
                title = "Đã tìm thấy tài xế";
                content = "Tài xế đã được giữ chỗ cho chuyến đi của bạn.";
            }
            case "trip.assigned" -> {
                title = "Tài xế đã được điều phối";
                content = "Tài xế đang kiểm tra và chuẩn bị tiếp nhận chuyến.";
            }
            case "trip.accepted" -> {
                title = "Tài xế đã nhận chuyến";
                content = "Tài xế đang di chuyển tới điểm đón của bạn.";
            }
            case "trip.started" -> {
                title = "Chuyến đi đã bắt đầu";
                content = "Chúc bạn một lộ trình an toàn và thuận lợi cùng GoRide!";
            }
            case "trip.completed" -> {
                title = "Chuyến đi đã hoàn thành";
                content = "Cảm ơn bạn đã lựa chọn GoRide. Chuyến đi của bạn đã kết thúc an toàn.";
            }
            case "trip.cancelled" -> {
                title = "Chuyến đi đã bị hủy";
                content = "Chuyến đi của bạn đã được hủy thành công.";
            }
            case "payment.succeeded" -> {
                title = "Thanh toán thành công";
                content = "Giao dịch thanh toán cước chuyến đi đã được thực hiện thành công.";
            }
            case "payment.failed" -> {
                title = "Thanh toán thất bại";
                content = "Giao dịch thanh toán tự động không thành công. Vui lòng thanh toán trực tiếp.";
            }
            default -> {
                title = "Cập nhật chuyến đi";
                content = "Hệ thống vừa cập nhật sự kiện: " + eventType;
            }
        }

        // 3. Serialize title & content
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("title", title);
        payloadMap.put("content", content);
        if (tripId != null) {
            payloadMap.put("tripId", tripId.toString());
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            payloadJson = "{\"title\":\"" + title + "\",\"content\":\"" + content + "\"}";
        }

        // 4. Save NotificationEntity
        Instant now = Instant.now();
        NotificationEntity entity = new NotificationEntity();
        entity.setSourceEventId(eventId);
        entity.setRecipientUserId(recipientUserId);
        entity.setRecipientRole(recipientRole);
        entity.setTripId(tripId);
        entity.setType(eventType);
        entity.setChannel(NotificationChannel.SIMULATED);
        entity.setStatus(NotificationStatus.SENT);
        entity.setPayload(payloadJson);
        entity.setAttemptCount(1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setSentAt(now);

        NotificationEntity saved = notificationRepository.save(entity);

        // 5. Save InboxEventEntity for deduplication
        InboxEventEntity inboxEvent = new InboxEventEntity(
                eventId,
                eventType,
                "notification-service",
                correlationId
        );
        inboxEventRepository.save(inboxEvent);

        log.info("Saved notification id={} for eventId={}, recipientUserId={}", saved.getId(), eventId, recipientUserId);
        return NotificationResponse.from(saved, objectMapper);
    }
}

