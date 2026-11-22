package com.goride.matching.adapter.in.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.event.EventEnvelope;
import com.goride.matching.application.port.in.ProcessTripCreatedCommand;
import com.goride.matching.application.port.in.ProcessTripCreatedUseCase;
import com.goride.matching.domain.model.InboxEvent;
import com.goride.matching.domain.repository.InboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
public class TripCreatedMessageListener {

    private static final Logger log = LoggerFactory.getLogger(TripCreatedMessageListener.class);
    private static final String CONSUMER_NAME = "matching-trip-created-listener";

    private final ObjectMapper objectMapper;
    private final InboxEventRepository inboxEventRepository;
    private final ProcessTripCreatedUseCase processTripCreatedUseCase;

    public TripCreatedMessageListener(ObjectMapper objectMapper,
                                      InboxEventRepository inboxEventRepository,
                                      ProcessTripCreatedUseCase processTripCreatedUseCase) {
        this.objectMapper = objectMapper;
        this.inboxEventRepository = inboxEventRepository;
        this.processTripCreatedUseCase = processTripCreatedUseCase;
    }

    @RabbitListener(queues = "${goride.rabbitmq.queues.trip-created:matching.trip-created.q}")
    public void onTripCreatedMessage(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        EventEnvelope<TripCreatedEventPayload> envelope;
        try {
            envelope = objectMapper.readValue(body, new TypeReference<>() {});
        } catch (Exception ex) {
            log.error("Failed to deserialize trip.created EventEnvelope: {}", body, ex);
            throw new IllegalArgumentException("Malformed trip.created message payload", ex);
        }

        UUID eventId = envelope.getEventId();
        if (eventId == null || envelope.getPayload() == null) {
            throw new IllegalArgumentException("Missing eventId or payload in trip.created envelope");
        }

        handleEventEnvelope(envelope);
    }

    public void handleEventEnvelope(EventEnvelope<TripCreatedEventPayload> envelope) {
        UUID eventId = envelope.getEventId();
        if (inboxEventRepository.existsByEventId(eventId)) {
            log.info("Duplicate eventId={} detected in inbox_event. Skipping idempotently.", eventId);
            return;
        }

        TripCreatedEventPayload payload = envelope.getPayload();
        UUID correlationId = envelope.getCorrelationId() != null ? envelope.getCorrelationId() : UUID.randomUUID();

        ProcessTripCreatedCommand command = new ProcessTripCreatedCommand(
                eventId,
                payload.tripId(),
                payload.passengerId(),
                payload.pickupLatitude(),
                payload.pickupLongitude(),
                correlationId
        );

        processTripCreatedUseCase.processTripCreated(command);

        inboxEventRepository.save(new InboxEvent(
                eventId,
                envelope.getEventType() != null ? envelope.getEventType() : "trip.created",
                CONSUMER_NAME,
                correlationId,
                Instant.now()
        ));
    }
}
