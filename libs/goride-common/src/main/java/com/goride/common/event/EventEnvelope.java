package com.goride.common.event;

import java.time.Instant;
import java.util.UUID;

public class EventEnvelope<T> {

    private UUID eventId;
    private String eventType;
    private Integer eventVersion = 1;
    private UUID correlationId;
    private UUID causationId;
    private String aggregateId;
    private String producer;
    private Instant occurredAt;
    private T payload;

    public EventEnvelope() {
    }

    public EventEnvelope(UUID eventId, String eventType, Integer eventVersion, UUID correlationId,
                         UUID causationId, String aggregateId, String producer, Instant occurredAt, T payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.eventVersion = eventVersion != null ? eventVersion : 1;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.aggregateId = aggregateId;
        this.producer = producer;
        this.occurredAt = occurredAt != null ? occurredAt : Instant.now();
        this.payload = payload;
    }

    public static <T> EventEnvelope<T> of(String eventType, String aggregateId, String producer,
                                          UUID correlationId, UUID causationId, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                1,
                correlationId != null ? correlationId : UUID.randomUUID(),
                causationId,
                aggregateId,
                producer,
                Instant.now(),
                payload
        );
    }

    public static <T> EventEnvelope<T> of(UUID eventId, String eventType, String aggregateId,
                                          String producer, UUID correlationId, UUID causationId, T payload) {
        return new EventEnvelope<>(
                eventId != null ? eventId : UUID.randomUUID(),
                eventType,
                1,
                correlationId != null ? correlationId : UUID.randomUUID(),
                causationId,
                aggregateId,
                producer,
                Instant.now(),
                payload
        );
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Integer getEventVersion() {
        return eventVersion;
    }

    public void setEventVersion(Integer eventVersion) {
        this.eventVersion = eventVersion;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }

    public UUID getCausationId() {
        return causationId;
    }

    public void setCausationId(UUID causationId) {
        this.causationId = causationId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }
}
