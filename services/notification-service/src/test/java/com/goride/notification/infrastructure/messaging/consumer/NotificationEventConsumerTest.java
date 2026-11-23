package com.goride.notification.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.goride.common.event.EventEnvelope;
import com.goride.notification.domain.model.NotificationStatus;
import com.goride.notification.infrastructure.persistence.entity.InboxEventEntity;
import com.goride.notification.infrastructure.persistence.entity.NotificationEntity;
import com.goride.notification.infrastructure.persistence.repository.NotificationInboxEventRepository;
import com.goride.notification.infrastructure.persistence.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private NotificationInboxEventRepository inboxEventRepository;

    @Mock
    private NotificationRepository notificationRepository;

    private ObjectMapper objectMapper;
    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new NotificationEventConsumer(objectMapper, inboxEventRepository, notificationRepository);
    }

    @Test
    @DisplayName("Inbox Deduplication: Skips processing when eventId is already processed")
    void handleEventEnvelope_DuplicateEventId_ShouldSkip() throws Exception {
        UUID eventId = UUID.randomUUID();
        JsonNode payload = objectMapper.readTree("{\"passengerId\":\"" + UUID.randomUUID() + "\",\"amount\":50000}");
        EventEnvelope<JsonNode> envelope = EventEnvelope.of(
                eventId, "payment.succeeded", "agg-1", "payment-service",
                UUID.randomUUID(), null, payload
        );

        when(inboxEventRepository.existsByEventIdAndConsumer(eventId, "notification-event-consumer"))
                .thenReturn(true);

        consumer.handleEventEnvelope(envelope);

        verify(notificationRepository, never()).save(any(NotificationEntity.class));
        verify(inboxEventRepository, never()).save(any(InboxEventEntity.class));
    }

    @Test
    @DisplayName("Event payment.succeeded: Creates PAYMENT_SUCCESS notification and saves inbox")
    void handleEventEnvelope_PaymentSucceeded_CreatesNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        String json = "{\"passengerId\":\"" + passengerId + "\",\"tripId\":\"" + tripId + "\",\"amount\":75000}";
        JsonNode payload = objectMapper.readTree(json);
        EventEnvelope<JsonNode> envelope = EventEnvelope.of(
                eventId, "payment.succeeded", tripId.toString(), "payment-service",
                UUID.randomUUID(), null, payload
        );

        when(inboxEventRepository.existsByEventIdAndConsumer(eventId, "notification-event-consumer"))
                .thenReturn(false);

        consumer.handleEventEnvelope(envelope);

        verify(inboxEventRepository).save(any(InboxEventEntity.class));

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        NotificationEntity saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo("PAYMENT_SUCCESS");
        assertThat(saved.getRecipientUserId()).isEqualTo(passengerId);
        assertThat(saved.getTripId()).isEqualTo(tripId);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getPayload()).contains("Thanh toán thành công");
    }

    @Test
    @DisplayName("Event payment.failed: Creates PAYMENT_FAILED notification")
    void handleEventEnvelope_PaymentFailed_CreatesNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        String json = "{\"passengerId\":\"" + passengerId + "\",\"failureCode\":\"INSUFFICIENT_FUNDS\"}";
        JsonNode payload = objectMapper.readTree(json);
        EventEnvelope<JsonNode> envelope = EventEnvelope.of(
                eventId, "payment.failed", "agg-1", "payment-service",
                UUID.randomUUID(), null, payload
        );

        when(inboxEventRepository.existsByEventIdAndConsumer(eventId, "notification-event-consumer"))
                .thenReturn(false);

        consumer.handleEventEnvelope(envelope);

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("PAYMENT_FAILED");
        assertThat(captor.getValue().getPayload()).contains("Thanh toán thất bại");
    }

    @Test
    @DisplayName("Event trip.completed: Creates TRIP_COMPLETED notification")
    void handleEventEnvelope_TripCompleted_CreatesNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        String json = "{\"passengerId\":\"" + passengerId + "\",\"tripId\":\"" + tripId + "\"}";
        JsonNode payload = objectMapper.readTree(json);
        EventEnvelope<JsonNode> envelope = EventEnvelope.of(
                eventId, "trip.completed", tripId.toString(), "trip-service",
                UUID.randomUUID(), null, payload
        );

        when(inboxEventRepository.existsByEventIdAndConsumer(eventId, "notification-event-consumer"))
                .thenReturn(false);

        consumer.handleEventEnvelope(envelope);

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("TRIP_COMPLETED");
        assertThat(captor.getValue().getPayload()).contains("Chuyến đi hoàn thành");
    }

    @Test
    @DisplayName("onNotificationEventMessage: Deserializes message and executes pipeline")
    void onNotificationEventMessage_Success() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        JsonNode payload = objectMapper.readTree("{\"passengerId\":\"" + passengerId + "\"}");
        EventEnvelope<JsonNode> envelope = EventEnvelope.of(
                eventId, "trip.started", "trip-1", "trip-service",
                UUID.randomUUID(), null, payload
        );

        String json = objectMapper.writeValueAsString(envelope);
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(inboxEventRepository.existsByEventIdAndConsumer(eventId, "notification-event-consumer"))
                .thenReturn(true);

        consumer.onNotificationEventMessage(message);

        verify(inboxEventRepository).existsByEventIdAndConsumer(eventId, "notification-event-consumer");
    }

    @Test
    @DisplayName("onNotificationEventMessage: Throws IllegalArgumentException on invalid JSON")
    void onNotificationEventMessage_Malformed_ThrowsException() {
        Message message = new Message("not-valid-json".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        assertThatThrownBy(() -> consumer.onNotificationEventMessage(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed notification event message payload");
    }

    @Test
    @DisplayName("E2E-04: Same event delivered 5 times results in exactly 1 inbox record and 1 notification")
    void handleEventEnvelope_DeliveredFiveTimes_ProcessedExactlyOnce() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        JsonNode payload = objectMapper.readTree("{\"passengerId\":\"" + passengerId + "\",\"amount\":100000}");
        EventEnvelope<JsonNode> envelope = EventEnvelope.of(
                eventId, "payment.succeeded", "agg-1", "payment-service",
                UUID.randomUUID(), null, payload
        );

        when(inboxEventRepository.existsByEventIdAndConsumer(eventId, "notification-event-consumer"))
                .thenReturn(false)
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(true);

        for (int i = 0; i < 5; i++) {
            consumer.handleEventEnvelope(envelope);
        }

        verify(notificationRepository, times(1)).save(any(NotificationEntity.class));
        verify(inboxEventRepository, times(1)).save(any(InboxEventEntity.class));
    }
}
