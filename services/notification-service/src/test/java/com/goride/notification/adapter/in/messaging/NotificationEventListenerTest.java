package com.goride.notification.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.event.EventEnvelope;
import com.goride.notification.application.service.NotificationService;
import com.goride.notification.infrastructure.persistence.repository.NotificationInboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationInboxEventRepository inboxEventRepository;

    @Mock
    private NotificationService notificationService;

    private ObjectMapper objectMapper;
    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        listener = new NotificationEventListener(objectMapper, inboxEventRepository, notificationService);
    }

    @Test
    @DisplayName("Consume business event successfully and dispatch to NotificationService")
    void onEventMessage_Success() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        String payloadJson = String.format("{\"tripId\":\"%s\",\"passengerId\":\"%s\"}", tripId, passengerId);
        JsonNode payloadNode = objectMapper.readTree(payloadJson);

        EventEnvelope<JsonNode> envelope = new EventEnvelope<>(
                eventId, "trip.created", 1, correlationId, null, tripId.toString(), "trip-service", Instant.now(), payloadNode
        );

        String json = objectMapper.writeValueAsString(envelope);
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(inboxEventRepository.existsByEventIdAndConsumer(eq(eventId), eq("notification-service"))).thenReturn(false);

        listener.onEventMessage(message);

        verify(notificationService).processEventNotification(
                eq(eventId), eq("trip.created"), eq(correlationId), any(JsonNode.class)
        );
    }

    @Test
    @DisplayName("Duplicate eventId is skipped idempotently via notification inbox check")
    void onEventMessage_DuplicateSkipped() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();

        String payloadJson = String.format("{\"tripId\":\"%s\"}", tripId);
        JsonNode payloadNode = objectMapper.readTree(payloadJson);

        EventEnvelope<JsonNode> envelope = new EventEnvelope<>(
                eventId, "trip.created", 1, UUID.randomUUID(), null, tripId.toString(), "trip-service", Instant.now(), payloadNode
        );

        String json = objectMapper.writeValueAsString(envelope);
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(inboxEventRepository.existsByEventIdAndConsumer(eq(eventId), eq("notification-service"))).thenReturn(true);

        listener.onEventMessage(message);

        verify(notificationService, never()).processEventNotification(any(), any(), any(), any());
    }
}
