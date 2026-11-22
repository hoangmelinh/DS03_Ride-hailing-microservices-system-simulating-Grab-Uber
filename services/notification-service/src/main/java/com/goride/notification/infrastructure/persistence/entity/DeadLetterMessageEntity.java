package com.goride.notification.infrastructure.persistence.entity;

import com.goride.notification.domain.model.DlqMessageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "dead_letter_messages",
    indexes = {
        @Index(name = "idx_notification_dlq_status", columnList = "status"),
        @Index(name = "idx_notification_dlq_queue", columnList = "queue_name")
    }
)
public class DeadLetterMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "queue_name", length = 100, nullable = false)
    private String queueName;

    @Column(name = "exchange_name", length = 100, nullable = false)
    private String exchangeName;

    @Column(name = "routing_key", length = 100, nullable = false)
    private String routingKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "exception_class", length = 255)
    private String exceptionClass;

    @Column(name = "exception_message", columnDefinition = "text")
    private String exceptionMessage;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private DlqMessageStatus status = DlqMessageStatus.DEAD;

    @Column(name = "failed_at", nullable = false)
    private Instant failedAt;

    @Column(name = "reprocessed_at")
    private Instant reprocessedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DeadLetterMessageEntity() {
    }

    public DeadLetterMessageEntity(String queueName, String exchangeName, String routingKey,
                                   String payload, UUID correlationId, String exceptionClass,
                                   String exceptionMessage) {
        this.queueName = queueName;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.payload = payload;
        this.correlationId = correlationId;
        this.exceptionClass = exceptionClass;
        this.exceptionMessage = exceptionMessage;
        this.retryCount = 3;
        this.status = DlqMessageStatus.DEAD;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.failedAt == null) {
            this.failedAt = now;
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.retryCount == null) {
            this.retryCount = 3;
        }
        if (this.status == null) {
            this.status = DlqMessageStatus.DEAD;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public void setExceptionClass(String exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public DlqMessageStatus getStatus() {
        return status;
    }

    public void setStatus(DlqMessageStatus status) {
        this.status = status;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }

    public Instant getReprocessedAt() {
        return reprocessedAt;
    }

    public void setReprocessedAt(Instant reprocessedAt) {
        this.reprocessedAt = reprocessedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
