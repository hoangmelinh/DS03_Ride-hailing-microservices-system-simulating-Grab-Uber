package com.goride.driver.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox_event")
public class InboxEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "consumer", nullable = false, length = 100)
    private String consumer;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public InboxEventJpaEntity() {
    }

    public InboxEventJpaEntity(UUID eventId, String eventType, String consumer, UUID correlationId, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.consumer = consumer;
        this.correlationId = correlationId;
        this.processedAt = processedAt != null ? processedAt : Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.processedAt == null) {
            this.processedAt = Instant.now();
        }
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

    public String getConsumer() {
        return consumer;
    }

    public void setConsumer(String consumer) {
        this.consumer = consumer;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
