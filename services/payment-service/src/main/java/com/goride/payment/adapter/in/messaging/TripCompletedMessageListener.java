package com.goride.payment.adapter.in.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.event.EventEnvelope;
import com.goride.payment.application.dto.event.TripCompletedEventPayload;
import com.goride.payment.application.service.PaymentService;
import com.goride.payment.infrastructure.persistence.repository.InboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class TripCompletedMessageListener {

    private static final Logger log = LoggerFactory.getLogger(TripCompletedMessageListener.class);
    private static final String CONSUMER_NAME = "payment-trip-completed-listener";

    private final ObjectMapper objectMapper;
    private final InboxEventRepository inboxEventRepository;
    private final PaymentService paymentService;

    public TripCompletedMessageListener(ObjectMapper objectMapper,
                                       InboxEventRepository inboxEventRepository,
                                       PaymentService paymentService) {
        this.objectMapper = objectMapper;
        this.inboxEventRepository = inboxEventRepository;
        this.paymentService = paymentService;
    }

    @RabbitListener(queues = "${goride.rabbitmq.queues.trip-completed:payment.trip-completed.q}")
    public void onTripCompletedMessage(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        EventEnvelope<TripCompletedEventPayload> envelope;
        try {
            envelope = objectMapper.readValue(body, new TypeReference<>() {});
        } catch (Exception ex) {
            log.error("Failed to deserialize trip.completed EventEnvelope: {}", body, ex);
            throw new IllegalArgumentException("Malformed trip.completed message payload", ex);
        }

        UUID eventId = envelope.getEventId();
        if (eventId == null || envelope.getPayload() == null) {
            throw new IllegalArgumentException("Missing eventId or payload in trip.completed envelope");
        }

        handleEventEnvelope(envelope);
    }

    public void handleEventEnvelope(EventEnvelope<TripCompletedEventPayload> envelope) {
        UUID eventId = envelope.getEventId();
        if (inboxEventRepository.existsByEventIdAndConsumer(eventId, CONSUMER_NAME)) {
            log.info("Duplicate eventId={} detected in inbox_event for {}. Skipping idempotently.",
                    eventId, CONSUMER_NAME);
            return;
        }

        TripCompletedEventPayload payload = envelope.getPayload();
        UUID correlationId = envelope.getCorrelationId() != null ? envelope.getCorrelationId() : UUID.randomUUID();

        paymentService.processPaymentFromEvent(
                eventId,
                payload.getTripId(),
                payload.getPassengerId(),
                payload.getFinalFare(),
                payload.getCurrency(),
                correlationId
        );
    }
}
