package com.goride.driver.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class OutboxEvent {

    private final UUID id;
    private final String aggregateType;
    private final UUID aggregateId;
    private final String eventType;
    private final Integer eventVersion;
    private final String payload;
    private final UUID correlationId;
    private final UUID causationId;
    private final Instant occurredAt;
    private final String status;
    private final Integer retryCount;
    private final Instant nextRetryAt;
    private final Instant publishedAt;
    private final Instant createdAt;

    public OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType,
                       Integer eventVersion, String payload, UUID correlationId, UUID causationId,
                       Instant occurredAt, String status, Integer retryCount, Instant nextRetryAt,
                       Instant publishedAt, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.eventVersion = eventVersion != null ? eventVersion : 1;
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
        this.causationId = causationId;
        this.occurredAt = occurredAt != null ? occurredAt : Instant.now();
        this.status = status != null ? status : "PENDING";
        this.retryCount = retryCount != null ? retryCount : 0;
        this.nextRetryAt = nextRetryAt;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static OutboxEvent create(String aggregateType, UUID aggregateId, String eventType,
                                     Integer eventVersion, String payload, UUID correlationId, UUID causationId) {
        Instant now = Instant.now();
        return new OutboxEvent(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                eventType,
                eventVersion,
                payload,
                correlationId,
                causationId,
                now,
                "PENDING",
                0,
                null,
                null,
                now
        );
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public Integer getEventVersion() {
        return eventVersion;
    }

    public String getPayload() {
        return payload;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public UUID getCausationId() {
        return causationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getStatus() {
        return status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

