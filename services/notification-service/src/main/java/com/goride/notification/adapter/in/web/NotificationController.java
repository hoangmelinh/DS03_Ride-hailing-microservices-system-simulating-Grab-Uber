package com.goride.notification.adapter.in.web;

import com.goride.common.dto.ApiResponse;
import com.goride.notification.application.dto.request.SendNotificationRequest;
import com.goride.notification.application.dto.response.NotificationResponse;
import com.goride.notification.application.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
@Validated
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
     * Retrieve notification history for a specific user with pagination support.
     * Defaults to page=0, size=20.
     *
     * @param userId the user identifier
     * @param page zero-based page index (min: 0, default: 0)
     * @param size number of records per page (min: 1, max: 100, default: 20)
     * @return ApiResponse containing list of notifications ordered by createdAt descending
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotificationsByUserId(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be greater than or equal to 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1") @Max(value = 100, message = "size must not exceed 100") int size) {
        log.info("Received REST request to get notifications for userId: {}, page: {}, size: {}", userId, page, size);
        Pageable pageable = PageRequest.of(page, size);
        List<NotificationResponse> list = notificationService.getNotificationsByUserId(userId, pageable).getContent();
        return ResponseEntity.ok(ApiResponse.success(list));
    }
}

