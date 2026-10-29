package com.goride.notification.application.dto.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.notification.domain.model.NotificationChannel;
import com.goride.notification.domain.model.NotificationStatus;
import com.goride.notification.infrastructure.persistence.entity.NotificationEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing notification details.
 */
public class NotificationResponse {

    private UUID id;
    private UUID sourceEventId;
    private UUID recipientUserId;
    private String recipientRole;
    private UUID tripId;
    private String type;
    private NotificationChannel channel;
    private NotificationStatus status;
    private String title;
    private String content;
    private String payload;
    private Integer attemptCount;
    private Instant sentAt;
    private Instant createdAt;

    public NotificationResponse() {
    }

    public static NotificationResponse from(NotificationEntity entity, ObjectMapper objectMapper) {
        if (entity == null) {
            return null;
        }

        NotificationResponse response = new NotificationResponse();
        response.setId(entity.getId());
        response.setSourceEventId(entity.getSourceEventId());
        response.setRecipientUserId(entity.getRecipientUserId());
        response.setRecipientRole(entity.getRecipientRole());
        response.setTripId(entity.getTripId());
        response.setType(entity.getType());
        response.setChannel(entity.getChannel());
        response.setStatus(entity.getStatus());
        response.setPayload(entity.getPayload());
        response.setAttemptCount(entity.getAttemptCount());
        response.setSentAt(entity.getSentAt());
        response.setCreatedAt(entity.getCreatedAt());

        // Parse title & content from payload JSONB if available
        if (entity.getPayload() != null && objectMapper != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(entity.getPayload());
                if (jsonNode.hasNonNull("title")) {
                    response.setTitle(jsonNode.get("title").asText());
                }
                if (jsonNode.hasNonNull("content")) {
                    response.setContent(jsonNode.get("content").asText());
                }
            } catch (JsonProcessingException ignored) {
                // If payload is not valid JSON, leave title and content null
            }
        }

        return response;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public void setSourceEventId(UUID sourceEventId) {
        this.sourceEventId = sourceEventId;
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(UUID recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public String getRecipientRole() {
        return recipientRole;
    }

    public void setRecipientRole(String recipientRole) {
        this.recipientRole = recipientRole;
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

