package com.goride.matching.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class InboxEvent {

    private final UUID eventId;
    private final String eventType;
    private final String consumer;
    private final UUID correlationId;
    private final Instant processedAt;

    public InboxEvent(UUID eventId, String eventType, String consumer, UUID correlationId, Instant processedAt) {
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.consumer = Objects.requireNonNull(consumer, "consumer must not be null");
        this.correlationId = correlationId;
        this.processedAt = processedAt != null ? processedAt : Instant.now();
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getConsumer() { return consumer; }
    public UUID getCorrelationId() { return correlationId; }
    public Instant getProcessedAt() { return processedAt; }
}
