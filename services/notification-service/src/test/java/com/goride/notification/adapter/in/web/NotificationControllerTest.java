package com.goride.notification.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.notification.application.dto.request.SendNotificationRequest;
import com.goride.notification.application.dto.response.NotificationResponse;
import com.goride.notification.application.service.NotificationService;
import com.goride.notification.domain.model.NotificationChannel;
import com.goride.notification.domain.model.NotificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {NotificationController.class, NotificationGlobalExceptionHandler.class})
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    @Test
    @DisplayName("POST /api/v1/notifications/send with valid body returns 200 and ApiResponse envelope")
    void sendNotification_ValidRequest_Returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        SendNotificationRequest request = new SendNotificationRequest(
                userId,
                "Tài xế đang đến",
                "Tài xế Nguyễn Văn A đang đến điểm đón",
                "DRIVER_ARRIVING"
        );

        NotificationResponse mockResponse = new NotificationResponse();
        mockResponse.setId(UUID.randomUUID());
        mockResponse.setSourceEventId(UUID.randomUUID());
        mockResponse.setRecipientUserId(userId);
        mockResponse.setRecipientRole("PASSENGER");
        mockResponse.setType("DRIVER_ARRIVING");
        mockResponse.setChannel(NotificationChannel.SIMULATED);
        mockResponse.setStatus(NotificationStatus.SENT);
        mockResponse.setTitle("Tài xế đang đến");
        mockResponse.setContent("Tài xế Nguyễn Văn A đang đến điểm đón");
        mockResponse.setSentAt(Instant.now());
        mockResponse.setCreatedAt(Instant.now());

        when(notificationService.sendNotification(any(SendNotificationRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(mockResponse.getId().toString()))
                .andExpect(jsonPath("$.data.recipientUserId").value(userId.toString()))
                .andExpect(jsonPath("$.data.title").value("Tài xế đang đến"))
                .andExpect(jsonPath("$.data.type").value("DRIVER_ARRIVING"))
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/notifications/send missing required fields returns 400 Bad Request")
    void sendNotification_MissingFields_Returns400() throws Exception {
        SendNotificationRequest request = new SendNotificationRequest();
        // userId, title, content, type are all null

        mockMvc.perform(post("/api/v1/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/{userId} with default pagination parameters returns 200 and page=0, size=20")
    void getNotificationsByUserId_DefaultPagination_Returns200WithList() throws Exception {
        UUID userId = UUID.randomUUID();

        NotificationResponse noti1 = new NotificationResponse();
        noti1.setId(UUID.randomUUID());
        noti1.setRecipientUserId(userId);
        noti1.setTitle("Thông báo 1");
        noti1.setType("TRIP_COMPLETED");
        noti1.setStatus(NotificationStatus.SENT);

        when(notificationService.getNotificationsByUserId(eq(userId), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(noti1)));

        mockMvc.perform(get("/api/v1/notifications/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].recipientUserId").value(userId.toString()))
                .andExpect(jsonPath("$.data[0].title").value("Thông báo 1"));

        verify(notificationService).getNotificationsByUserId(eq(userId), eq(PageRequest.of(0, 20)));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/{userId} with custom pagination parameters (page=1, size=5) returns 200")
    void getNotificationsByUserId_CustomPagination_Returns200() throws Exception {
        UUID userId = UUID.randomUUID();

        NotificationResponse noti2 = new NotificationResponse();
        noti2.setId(UUID.randomUUID());
        noti2.setRecipientUserId(userId);
        noti2.setTitle("Thông báo 2");
        noti2.setType("PAYMENT_SUCCESS");
        noti2.setStatus(NotificationStatus.SENT);

        when(notificationService.getNotificationsByUserId(eq(userId), eq(PageRequest.of(1, 5))))
                .thenReturn(new PageImpl<>(List.of(noti2)));

        mockMvc.perform(get("/api/v1/notifications/{userId}", userId)
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("Thông báo 2"));

        verify(notificationService).getNotificationsByUserId(eq(userId), eq(PageRequest.of(1, 5)));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/{userId} when empty returns 200 with empty list")
    void getNotificationsByUserId_Empty_Returns200WithEmptyList() throws Exception {
        UUID userId = UUID.randomUUID();

        when(notificationService.getNotificationsByUserId(eq(userId), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/api/v1/notifications/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/{userId} with invalid page < 0 returns 400 with VALIDATION_ERROR")
    void getNotificationsByUserId_InvalidPage_Returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/notifications/{userId}", userId)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/{userId} with invalid size < 1 returns 400 with VALIDATION_ERROR")
    void getNotificationsByUserId_InvalidSizeBelowOne_Returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/notifications/{userId}", userId)
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/{userId} with invalid size > 100 returns 400 with VALIDATION_ERROR")
    void getNotificationsByUserId_InvalidSizeAboveMax_Returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/notifications/{userId}", userId)
                        .param("size", "150"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}

