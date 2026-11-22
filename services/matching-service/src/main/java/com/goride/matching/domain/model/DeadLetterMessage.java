package com.goride.matching.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class DeadLetterMessage {

    private final UUID id;
    private final String queueName;
    private final String exchangeName;
    private final String routingKey;
    private final String payload;
    private final UUID correlationId;
    private final String exceptionClass;
    private final String exceptionMessage;
    private final int retryCount;
    private final String status;
    private final Instant failedAt;
    private final Instant reprocessedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    public DeadLetterMessage(UUID id, String queueName, String exchangeName, String routingKey,
                             String payload, UUID correlationId, String exceptionClass, String exceptionMessage,
                             int retryCount, String status, Instant failedAt, Instant reprocessedAt,
                             Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.queueName = queueName;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.payload = payload;
        this.correlationId = correlationId;
        this.exceptionClass = exceptionClass;
        this.exceptionMessage = exceptionMessage;
        this.retryCount = retryCount;
        this.status = status != null ? status : "DEAD";
        this.failedAt = failedAt != null ? failedAt : Instant.now();
        this.reprocessedAt = reprocessedAt;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static DeadLetterMessage create(String queueName, String exchangeName, String routingKey,
                                           String payload, UUID correlationId, String exceptionClass,
                                           String exceptionMessage, int retryCount) {
        Instant now = Instant.now();
        return new DeadLetterMessage(
                UUID.randomUUID(),
                queueName,
                exchangeName,
                routingKey,
                payload,
                correlationId,
                exceptionClass,
                exceptionMessage,
                retryCount,
                "DEAD",
                now,
                null,
                now,
                now
        );
    }

    public UUID getId() { return id; }
    public String getQueueName() { return queueName; }
    public String getExchangeName() { return exchangeName; }
    public String getRoutingKey() { return routingKey; }
    public String getPayload() { return payload; }
    public UUID getCorrelationId() { return correlationId; }
    public String getExceptionClass() { return exceptionClass; }
    public String getExceptionMessage() { return exceptionMessage; }
    public int getRetryCount() { return retryCount; }
    public String getStatus() { return status; }
    public Instant getFailedAt() { return failedAt; }
    public Instant getReprocessedAt() { return reprocessedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
