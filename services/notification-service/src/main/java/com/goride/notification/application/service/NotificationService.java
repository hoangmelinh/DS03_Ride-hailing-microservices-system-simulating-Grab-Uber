package com.goride.notification.application.service;

import com.goride.notification.application.dto.request.SendNotificationRequest;
import com.goride.notification.application.dto.response.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service contract defining notification operations.
 */
public interface NotificationService {

    /**
     * Send simulated / mock notification and persist into database.
     *
     * @param request the send notification request
     * @return the created NotificationResponse
     */
    NotificationResponse sendNotification(SendNotificationRequest request);

    /**
     * Process asynchronous business event from RabbitMQ and generate notification.
     * Enforces Inbox deduplication and composite uniqueness constraint.
     */
    NotificationResponse processEventNotification(UUID eventId, String eventType, UUID correlationId,
                                                   com.fasterxml.jackson.databind.JsonNode payloadNode);

    /**
     * Retrieve list of notifications for a specific user ordered by createdAt descending.
     *
     * @param userId the user identifier
     * @return list of NotificationResponse
     */
    List<NotificationResponse> getNotificationsByUserId(UUID userId);

    /**
     * Retrieve paginated notifications for a specific user ordered by createdAt descending.
     *
     * @param userId the user identifier
     * @param pageable pagination options
     * @return page of NotificationResponse
     */
    Page<NotificationResponse> getNotificationsByUserId(UUID userId, Pageable pageable);
}

