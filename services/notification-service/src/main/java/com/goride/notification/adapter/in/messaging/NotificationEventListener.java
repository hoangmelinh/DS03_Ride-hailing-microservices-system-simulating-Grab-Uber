package com.goride.notification.adapter.in.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.event.EventEnvelope;
import com.goride.notification.application.service.NotificationService;
import com.goride.notification.infrastructure.persistence.repository.NotificationInboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
    private static final String CONSUMER_NAME = "notification-service";

    private final ObjectMapper objectMapper;
    private final NotificationInboxEventRepository inboxEventRepository;
    private final NotificationService notificationService;

    public NotificationEventListener(ObjectMapper objectMapper,
                                     NotificationInboxEventRepository inboxEventRepository,
                                     NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.inboxEventRepository = inboxEventRepository;
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "${goride.rabbitmq.queues.events:notification.events.q}")
    public void onEventMessage(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        EventEnvelope<JsonNode> envelope;
        try {
            envelope = objectMapper.readValue(body, new TypeReference<>() {});
        } catch (Exception ex) {
            log.error("Failed to deserialize EventEnvelope in notification listener: {}", body, ex);
            throw new IllegalArgumentException("Malformed event message payload", ex);
        }

        UUID eventId = envelope.getEventId();
        if (eventId == null) {
            log.warn("Missing eventId in envelope, skipping notification: {}", body);
            return;
        }

        handleEventEnvelope(envelope);
    }

    public void handleEventEnvelope(EventEnvelope<JsonNode> envelope) {
        UUID eventId = envelope.getEventId();
        if (inboxEventRepository.existsByEventIdAndConsumer(eventId, CONSUMER_NAME)) {
            log.info("Duplicate eventId={} detected in notification inbox_event. Skipping idempotently.", eventId);
            return;
        }

        UUID correlationId = envelope.getCorrelationId() != null ? envelope.getCorrelationId() : UUID.randomUUID();
        String eventType = envelope.getEventType() != null ? envelope.getEventType() : "event.unknown";

        notificationService.processEventNotification(
                eventId,
                eventType,
                correlationId,
                envelope.getPayload()
        );
    }
}
