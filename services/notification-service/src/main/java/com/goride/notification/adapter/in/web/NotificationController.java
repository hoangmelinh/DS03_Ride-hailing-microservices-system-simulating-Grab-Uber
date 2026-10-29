package com.goride.notification.adapter.in.web;

import com.goride.common.dto.ApiResponse;
import com.goride.notification.application.dto.request.SendNotificationRequest;
import com.goride.notification.application.dto.response.NotificationResponse;
import com.goride.notification.application.service.NotificationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * REST Controller exposing Notification endpoints.
 * Conforms to Clean Architecture and standard ApiResponse envelope.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
    }

    /**
     * Send a simulated notification to a user.
     *
     * @param request payload containing recipient, title, content, type
     * @return ApiResponse containing the created NotificationResponse
     */
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<NotificationResponse>> sendNotification(
            @Valid @RequestBody SendNotificationRequest request) {
        log.info("Received REST request to send notification to userId: {}", request.getUserId());
        NotificationResponse response = notificationService.sendNotification(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Retrieve notification history for a specific user.
     *
     * @param userId the user identifier
     * @return ApiResponse containing list of notifications ordered by createdAt descending
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotificationsByUserId(
            @PathVariable UUID userId) {
        log.info("Received REST request to get notifications for userId: {}", userId);
        List<NotificationResponse> list = notificationService.getNotificationsByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(list));
    }
}

