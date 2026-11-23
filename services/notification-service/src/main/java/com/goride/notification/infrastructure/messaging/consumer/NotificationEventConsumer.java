package com.goride.notification.infrastructure.messaging.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.event.EventEnvelope;
import com.goride.notification.domain.model.NotificationChannel;
import com.goride.notification.domain.model.NotificationStatus;
import com.goride.notification.infrastructure.persistence.entity.InboxEventEntity;
import com.goride.notification.infrastructure.persistence.entity.NotificationEntity;
import com.goride.notification.infrastructure.persistence.repository.NotificationInboxEventRepository;
import com.goride.notification.infrastructure.persistence.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer for domain events (payment.succeeded, payment.failed, trip.completed, etc.)
 * that trigger user notifications.
 * Implements Idempotent Consumer pattern with Inbox table (inbox_event).
 */
@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);
    private static final String CONSUMER_NAME = "notification-event-consumer";

    private final ObjectMapper objectMapper;
    private final NotificationInboxEventRepository inboxEventRepository;
    private final NotificationRepository notificationRepository;

    public NotificationEventConsumer(ObjectMapper objectMapper,
                                     NotificationInboxEventRepository inboxEventRepository,
                                     NotificationRepository notificationRepository) {
        this.objectMapper = objectMapper;
        this.inboxEventRepository = inboxEventRepository;
        this.notificationRepository = notificationRepository;
    }

    @RabbitListener(queues = "${goride.rabbitmq.queues.notification-events:notification.events.q}")
    @Transactional
    public void onNotificationEventMessage(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        EventEnvelope<JsonNode> envelope;
        try {
            envelope = objectMapper.readValue(body, new TypeReference<>() {});
        } catch (Exception ex) {
            log.error("Failed to deserialize notification event EventEnvelope: {}", body, ex);
            throw new IllegalArgumentException("Malformed notification event message payload", ex);
        }

        UUID eventId = envelope.getEventId();
        if (eventId == null || envelope.getPayload() == null) {
            throw new IllegalArgumentException("Missing eventId or payload in notification event envelope");
        }

        handleEventEnvelope(envelope);
    }

    @Transactional
    public void handleEventEnvelope(EventEnvelope<JsonNode> envelope) {
        UUID eventId = envelope.getEventId();
        if (inboxEventRepository.existsByEventIdAndConsumer(eventId, CONSUMER_NAME)) {
            log.info("Duplicate eventId={} for consumer={} detected in notification inbox_event. Skipping idempotently.",
                    eventId, CONSUMER_NAME);
            return;
        }

        String eventType = envelope.getEventType() != null ? envelope.getEventType() : "unknown";
        UUID correlationId = envelope.getCorrelationId() != null ? envelope.getCorrelationId() : UUID.randomUUID();
        JsonNode payload = envelope.getPayload();

        try {
            // 1. Record in inbox
            inboxEventRepository.save(new InboxEventEntity(
                    eventId,
                    eventType,
                    CONSUMER_NAME,
                    correlationId
            ));

            // 2. Dispatch notification based on event type
            createNotificationFromEvent(eventId, eventType, payload);

            log.info("Successfully processed notification eventId={}, eventType={}", eventId, eventType);

        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate notification detected for eventId={}: {}", eventId, ex.getMessage());
            // Handled gracefully due to idempotency
        }
    }

    private void createNotificationFromEvent(UUID eventId, String eventType, JsonNode payload) {
        UUID recipientUserId = null;
        UUID tripId = null;

        if (payload.hasNonNull("passengerId")) {
            recipientUserId = UUID.fromString(payload.get("passengerId").asText());
        } else if (payload.hasNonNull("userId")) {
            recipientUserId = UUID.fromString(payload.get("userId").asText());
        } else if (payload.hasNonNull("recipientUserId")) {
            recipientUserId = UUID.fromString(payload.get("recipientUserId").asText());
        }

        if (payload.hasNonNull("tripId")) {
            tripId = UUID.fromString(payload.get("tripId").asText());
        }

        if (recipientUserId == null) {
            log.warn("Could not determine recipientUserId for eventId={}, eventType={}. Skipping notification creation.",
                    eventId, eventType);
            return;
        }

        String title;
        String content;
        String notificationType;

        switch (eventType) {
            case "payment.succeeded" -> {
                notificationType = "PAYMENT_SUCCESS";
                title = "Thanh toán thành công";
                String amount = payload.hasNonNull("amount") ? payload.get("amount").asText() : "";
                content = "Chuyến đi của bạn đã được thanh toán thành công số tiền " + amount + " VND.";
            }
            case "payment.failed" -> {
                notificationType = "PAYMENT_FAILED";
                title = "Thanh toán thất bại";
                content = "Thanh toán cho chuyến đi của bạn thất bại. Vui lòng thử lại.";
            }
            case "trip.completed" -> {
                notificationType = "TRIP_COMPLETED";
                title = "Chuyến đi hoàn thành";
                content = "Chuyến đi của bạn đã hoàn thành. Cảm ơn bạn đã sử dụng GoRide!";
            }
            case "trip.started" -> {
                notificationType = "TRIP_STARTED";
                title = "Chuyến đi bắt đầu";
                content = "Tài xế đã bắt đầu chuyến đi của bạn.";
            }
            case "trip.driver-assigned" -> {
                notificationType = "DRIVER_ASSIGNED";
                title = "Đã tìm thấy tài xế";
                content = "Tài xế đang trên đường đến đón bạn.";
            }
            default -> {
                notificationType = eventType.toUpperCase().replace('.', '_');
                title = "Thông báo GoRide";
                content = "Bạn có thông báo mới từ hệ thống GoRide.";
            }
        }

        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("title", title);
        payloadMap.put("content", content);
        if (tripId != null) {
            payloadMap.put("tripId", tripId.toString());
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (Exception e) {
            payloadJson = "{\"title\":\"" + title + "\",\"content\":\"" + content + "\"}";
        }

        Instant now = Instant.now();
        NotificationEntity notification = new NotificationEntity();
        notification.setSourceEventId(eventId);
        notification.setRecipientUserId(recipientUserId);
        notification.setRecipientRole("PASSENGER");
        notification.setTripId(tripId);
        notification.setType(notificationType);
        notification.setChannel(NotificationChannel.SIMULATED);
        notification.setStatus(NotificationStatus.SENT);
        notification.setPayload(payloadJson);
        notification.setAttemptCount(1);
        notification.setCreatedAt(now);
        notification.setUpdatedAt(now);
        notification.setSentAt(now);

        notificationRepository.save(notification);
        log.info("Saved notification [id={}, type={}, recipientUserId={}] from eventId={}",
                notification.getId(), notificationType, recipientUserId, eventId);
    }
}
