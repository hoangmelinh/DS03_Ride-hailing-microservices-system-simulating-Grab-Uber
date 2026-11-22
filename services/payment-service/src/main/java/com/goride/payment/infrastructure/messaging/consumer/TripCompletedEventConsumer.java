package com.goride.payment.infrastructure.messaging.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.event.EventEnvelope;
import com.goride.payment.application.dto.event.TripCompletedEventPayload;
import com.goride.payment.application.service.PaymentService;
import com.goride.payment.infrastructure.persistence.entity.InboxEventEntity;
import com.goride.payment.infrastructure.persistence.repository.InboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Consumer for 'trip.completed' events published on RabbitMQ exchange.
 * Implements Idempotent Consumer pattern with Inbox table (inbox_event).
 */
@Component
public class TripCompletedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TripCompletedEventConsumer.class);
    private static final String CONSUMER_NAME = "payment-trip-completed-consumer";

    private final ObjectMapper objectMapper;
    private final InboxEventRepository inboxEventRepository;
    private final PaymentService paymentService;

    public TripCompletedEventConsumer(ObjectMapper objectMapper,
                                      InboxEventRepository inboxEventRepository,
                                      PaymentService paymentService) {
        this.objectMapper = objectMapper;
        this.inboxEventRepository = inboxEventRepository;
        this.paymentService = paymentService;
    }

    @RabbitListener(queues = "${goride.rabbitmq.queues.trip-completed:payment.trip-completed.q}")
    @Transactional
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

    @Transactional
    public void handleEventEnvelope(EventEnvelope<TripCompletedEventPayload> envelope) {
        UUID eventId = envelope.getEventId();
        if (inboxEventRepository.existsByEventIdAndConsumer(eventId, CONSUMER_NAME)) {
            log.info("Duplicate eventId={} for consumer={} detected in inbox_event. Skipping idempotently.",
                    eventId, CONSUMER_NAME);
            return;
        }

        TripCompletedEventPayload payload = envelope.getPayload();
        UUID correlationId = envelope.getCorrelationId() != null ? envelope.getCorrelationId() : UUID.randomUUID();

        try {
            // 1. Record in inbox
            inboxEventRepository.save(new InboxEventEntity(
                    eventId,
                    envelope.getEventType() != null ? envelope.getEventType() : "trip.completed",
                    CONSUMER_NAME,
                    correlationId
            ));

            // 2. Trigger payment processing
            paymentService.processPaymentFromTripCompleted(
                    payload.tripId(),
                    payload.passengerId(),
                    payload.finalFare(),
                    correlationId,
                    eventId
            );

            log.info("Successfully processed trip.completed eventId={} for tripId={}", eventId, payload.tripId());

        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate trip.completed processing detected for tripId={}, eventId={}: {}",
                    payload.tripId(), eventId, ex.getMessage());
            // Handled gracefully due to idempotency
        }
    }
}
