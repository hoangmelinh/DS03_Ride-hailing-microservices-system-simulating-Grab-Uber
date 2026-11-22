package com.goride.payment.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.goride.common.event.EventEnvelope;
import com.goride.payment.application.dto.event.TripCompletedEventPayload;
import com.goride.payment.application.service.PaymentService;
import com.goride.payment.infrastructure.persistence.entity.InboxEventEntity;
import com.goride.payment.infrastructure.persistence.repository.InboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripCompletedEventConsumerTest {

    @Mock
    private InboxEventRepository inboxEventRepository;

    @Mock
    private PaymentService paymentService;

    private ObjectMapper objectMapper;
    private TripCompletedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new TripCompletedEventConsumer(objectMapper, inboxEventRepository, paymentService);
    }

    @Test
    @DisplayName("Inbox Deduplication: Skips processing when eventId is already processed")
    void handleEventEnvelope_DuplicateEventId_ShouldSkipIdempotently() {
        UUID eventId = UUID.randomUUID();
        TripCompletedEventPayload payload = new TripCompletedEventPayload(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("65000.00"), "VND", "COMPLETED", Instant.now()
        );
        EventEnvelope<TripCompletedEventPayload> envelope = EventEnvelope.of(
                eventId, "trip.completed", payload.tripId().toString(), "trip-service",
                UUID.randomUUID(), null, payload
        );

        when(inboxEventRepository.existsByEventIdAndConsumer(eventId, "payment-trip-completed-consumer"))
                .thenReturn(true);

        consumer.handleEventEnvelope(envelope);

        verify(paymentService, never()).processPaymentFromTripCompleted(any(), any(), any(), any(), any());
        verify(inboxEventRepository, never()).save(any(InboxEventEntity.class));
    }

    @Test
    @DisplayName("New event: Persists InboxEvent and invokes PaymentService to charge fare")
    void handleEventEnvelope_NewEvent_ProcessesPaymentAndSavesInbox() {
        UUID eventId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        BigDecimal fare = new BigDecimal("85000.00");

        TripCompletedEventPayload payload = new TripCompletedEventPayload(
                tripId, passengerId, UUID.randomUUID(), fare, "VND", "COMPLETED", Instant.now()
        );
        EventEnvelope<TripCompletedEventPayload> envelope = EventEnvelope.of(
                eventId, "trip.completed", tripId.toString(), "trip-service",
                correlationId, null, payload
        );

        when(inboxEventRepository.existsByEventIdAndConsumer(eventId, "payment-trip-completed-consumer"))
                .thenReturn(false);

        consumer.handleEventEnvelope(envelope);

        verify(inboxEventRepository).save(any(InboxEventEntity.class));
        verify(paymentService).processPaymentFromTripCompleted(
                eq(tripId), eq(passengerId), eq(fare), eq(correlationId), eq(eventId)
        );
    }

    @Test
    @DisplayName("Concurrent duplicate: Handles DataIntegrityViolationException gracefully")
    void handleEventEnvelope_ConcurrentDuplicate_CatchesException() {
        UUID eventId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        TripCompletedEventPayload payload = new TripCompletedEventPayload(
                tripId, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("50000.00"), "VND", "COMPLETED", Instant.now()
        );
        EventEnvelope<TripCompletedEventPayload> envelope = EventEnvelope.of(
                eventId, "trip.completed", tripId.toString(), "trip-service",
                UUID.randomUUID(), null, payload
        );

        when(inboxEventRepository.existsByEventIdAndConsumer(eventId, "payment-trip-completed-consumer"))
                .thenReturn(false);
        when(inboxEventRepository.save(any(InboxEventEntity.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate inbox key"));

        consumer.handleEventEnvelope(envelope);

        // Verification: did not throw uncaught exception
        verify(inboxEventRepository).save(any(InboxEventEntity.class));
    }

    @Test
    @DisplayName("onTripCompletedMessage: Successfully deserializes and processes valid message")
    void onTripCompletedMessage_ValidMessage_Success() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        TripCompletedEventPayload payload = new TripCompletedEventPayload(
                tripId, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("45000.00"), "VND", "COMPLETED", Instant.now()
        );
        EventEnvelope<TripCompletedEventPayload> envelope = EventEnvelope.of(
                eventId, "trip.completed", tripId.toString(), "trip-service",
                UUID.randomUUID(), null, payload
        );

        String json = objectMapper.writeValueAsString(envelope);
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(inboxEventRepository.existsByEventIdAndConsumer(eventId, "payment-trip-completed-consumer"))
                .thenReturn(true);

        consumer.onTripCompletedMessage(message);

        verify(inboxEventRepository).existsByEventIdAndConsumer(eventId, "payment-trip-completed-consumer");
    }

    @Test
    @DisplayName("onTripCompletedMessage: Throws IllegalArgumentException on malformed JSON")
    void onTripCompletedMessage_MalformedJson_ThrowsException() {
        Message message = new Message("invalid-json".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        assertThatThrownBy(() -> consumer.onTripCompletedMessage(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed trip.completed message payload");
    }

    @Test
    @DisplayName("Duplicate tripId with different eventId: Calls PaymentService idempotently without throwing")
    void handleEventEnvelope_DuplicateTripIdWithDifferentEventId_HandledGracefully() {
        UUID eventId2 = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        BigDecimal fare = new BigDecimal("70000.00");

        TripCompletedEventPayload payload = new TripCompletedEventPayload(
                tripId, passengerId, UUID.randomUUID(), fare, "VND", "COMPLETED", Instant.now()
        );
        EventEnvelope<TripCompletedEventPayload> envelope2 = EventEnvelope.of(
                eventId2, "trip.completed", tripId.toString(), "trip-service",
                UUID.randomUUID(), null, payload
        );

        when(inboxEventRepository.existsByEventIdAndConsumer(eventId2, "payment-trip-completed-consumer"))
                .thenReturn(false);

        consumer.handleEventEnvelope(envelope2);

        verify(inboxEventRepository).save(any(InboxEventEntity.class));
        verify(paymentService).processPaymentFromTripCompleted(eq(tripId), eq(passengerId), eq(fare), any(), eq(eventId2));
    }
}
