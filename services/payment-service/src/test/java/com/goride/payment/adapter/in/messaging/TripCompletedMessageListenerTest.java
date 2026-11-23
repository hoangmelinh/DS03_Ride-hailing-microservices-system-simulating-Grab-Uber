package com.goride.payment.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.goride.common.event.EventEnvelope;
import com.goride.payment.application.dto.event.TripCompletedEventPayload;
import com.goride.payment.application.service.PaymentService;
import com.goride.payment.infrastructure.persistence.repository.InboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripCompletedMessageListenerTest {

    @Mock
    private InboxEventRepository inboxEventRepository;

    @Mock
    private PaymentService paymentService;

    private ObjectMapper objectMapper;
    private TripCompletedMessageListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        listener = new TripCompletedMessageListener(objectMapper, inboxEventRepository, paymentService);
    }

    @Test
    @DisplayName("Consume trip.completed event successfully and invoke PaymentService")
    void onTripCompletedMessage_Success() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        TripCompletedEventPayload payload = new TripCompletedEventPayload(
                tripId, passengerId, driverId, new BigDecimal("85000.00"), "VND", "COMPLETED", Instant.now()
        );

        EventEnvelope<TripCompletedEventPayload> envelope = new EventEnvelope<>(
                eventId, "trip.completed", 1, correlationId, null, tripId.toString(), "trip-service", Instant.now(), payload
        );

        String json = objectMapper.writeValueAsString(envelope);
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(inboxEventRepository.existsByEventIdAndConsumer(eq(eventId), anyString())).thenReturn(false);

        listener.onTripCompletedMessage(message);

        verify(paymentService).processPaymentFromEvent(
                eq(eventId), eq(tripId), eq(passengerId), eq(new BigDecimal("85000.00")), eq("VND"), eq(correlationId)
        );
    }

    @Test
    @DisplayName("Duplicate eventId is skipped idempotently via inbox check")
    void onTripCompletedMessage_DuplicateSkipped() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();

        TripCompletedEventPayload payload = new TripCompletedEventPayload(
                tripId, passengerId, UUID.randomUUID(), new BigDecimal("50000.00"), "VND", "COMPLETED", Instant.now()
        );

        EventEnvelope<TripCompletedEventPayload> envelope = new EventEnvelope<>(
                eventId, "trip.completed", 1, UUID.randomUUID(), null, tripId.toString(), "trip-service", Instant.now(), payload
        );

        String json = objectMapper.writeValueAsString(envelope);
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(inboxEventRepository.existsByEventIdAndConsumer(eq(eventId), anyString())).thenReturn(true);

        listener.onTripCompletedMessage(message);

        verify(paymentService, never()).processPaymentFromEvent(any(), any(), any(), any(), any(), any());
    }
}
