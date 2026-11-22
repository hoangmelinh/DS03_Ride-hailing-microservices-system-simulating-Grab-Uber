package com.goride.notification.application.dto.request;

import com.goride.notification.domain.model.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request payload for sending a mock/simulated notification.
 */
public class SendNotificationRequest {

    @NotNull(message = "userId is required")
    private UUID userId;

    @NotBlank(message = "title must not be blank")
    @Size(max = 255, message = "title must not exceed 255 characters")
    private String title;

    @NotBlank(message = "content must not be blank")
    @Size(max = 1000, message = "content must not exceed 1000 characters")
    private String content;

    @NotBlank(message = "type must not be blank")
    @Size(max = 50, message = "type must not exceed 50 characters")
    private String type;

    @Size(max = 30, message = "recipientRole must not exceed 30 characters")
    private String recipientRole;

    private UUID tripId;

    private NotificationChannel channel;

    public SendNotificationRequest() {
    }

    public SendNotificationRequest(UUID userId, String title, String content, String type) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.type = type;
    }

    public SendNotificationRequest(UUID userId, String title, String content, String type, String recipientRole, UUID tripId, NotificationChannel channel) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.type = type;
        this.recipientRole = recipientRole;
        this.tripId = tripId;
        this.channel = channel;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }
}

