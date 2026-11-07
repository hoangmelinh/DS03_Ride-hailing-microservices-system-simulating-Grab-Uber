package com.goride.notification.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.notification.application.dto.request.SendNotificationRequest;
import com.goride.notification.application.dto.response.NotificationResponse;
import com.goride.notification.application.service.impl.NotificationServiceImpl;
import com.goride.notification.domain.model.NotificationChannel;
import com.goride.notification.domain.model.NotificationStatus;
import com.goride.notification.infrastructure.persistence.entity.NotificationEntity;
import com.goride.notification.infrastructure.persistence.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    private ObjectMapper objectMapper;
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        notificationService = new NotificationServiceImpl(notificationRepository, objectMapper);
    }

    @Test
    @DisplayName("sendNotification creates entity with serialized JSONB payload and SENT status")
    void sendNotification_Success() {
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        SendNotificationRequest request = new SendNotificationRequest(
                userId,
                "Chuyến đi đã bắt đầu",
                "Tài xế đã bắt đầu hành trình đến điểm đến",
                "TRIP_STARTED"
        );
        request.setTripId(tripId);
        request.setRecipientRole("PASSENGER");

        when(notificationRepository.save(any(NotificationEntity.class)))
                .thenAnswer(invocation -> {
                    NotificationEntity entity = invocation.getArgument(0);
                    entity.setId(UUID.randomUUID());
                    entity.setCreatedAt(Instant.now());
                    return entity;
                });

        NotificationResponse response = notificationService.sendNotification(request);

        assertThat(response).isNotNull();
        assertThat(response.getRecipientUserId()).isEqualTo(userId);
        assertThat(response.getTitle()).isEqualTo("Chuyến đi đã bắt đầu");
        assertThat(response.getContent()).isEqualTo("Tài xế đã bắt đầu hành trình đến điểm đến");
        assertThat(response.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(response.getChannel()).isEqualTo(NotificationChannel.SIMULATED);

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        NotificationEntity savedEntity = captor.getValue();

        assertThat(savedEntity.getSourceEventId()).isNotNull();
        assertThat(savedEntity.getPayload()).contains("\"title\":\"Chuyến đi đã bắt đầu\"");
        assertThat(savedEntity.getPayload()).contains("\"content\":\"Tài xế đã bắt đầu hành trình đến điểm đến\"");
        assertThat(savedEntity.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getNotificationsByUserId returns mapped list with parsed title and content")
    void getNotificationsByUserId_ReturnsMappedList() {
        UUID userId = UUID.randomUUID();

        NotificationEntity entity = new NotificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setSourceEventId(UUID.randomUUID());
        entity.setRecipientUserId(userId);
        entity.setType("PAYMENT_SUCCESS");
        entity.setStatus(NotificationStatus.SENT);
        entity.setChannel(NotificationChannel.SIMULATED);
        entity.setPayload("{\"title\":\"Thanh toán thành công\",\"content\":\"Cuốc xe đã được thanh toán 50.000 VND\"}");
        entity.setCreatedAt(Instant.now());

        when(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(entity));

        List<NotificationResponse> responses = notificationService.getNotificationsByUserId(userId);

        assertThat(responses).hasSize(1);
        NotificationResponse res = responses.get(0);
        assertThat(res.getTitle()).isEqualTo("Thanh toán thành công");
        assertThat(res.getContent()).isEqualTo("Cuốc xe đã được thanh toán 50.000 VND");
        assertThat(res.getType()).isEqualTo("PAYMENT_SUCCESS");
    }

    @Test
    @DisplayName("getNotificationsByUserId with Pageable returns mapped Page")
    void getNotificationsByUserId_Pageable_ReturnsMappedPage() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        NotificationEntity entity = new NotificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setSourceEventId(UUID.randomUUID());
        entity.setRecipientUserId(userId);
        entity.setType("TRIP_COMPLETED");
        entity.setStatus(NotificationStatus.SENT);
        entity.setPayload("{\"title\":\"Hoàn thành\",\"content\":\"Bạn đã đến nơi\"}");
        entity.setCreatedAt(Instant.now());

        Page<NotificationEntity> entityPage = new PageImpl<>(List.of(entity), pageable, 1);
        when(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId, pageable))
                .thenReturn(entityPage);

        Page<NotificationResponse> resultPage = notificationService.getNotificationsByUserId(userId, pageable);

        assertThat(resultPage).isNotNull();
        assertThat(resultPage.getTotalElements()).isEqualTo(1);
        assertThat(resultPage.getContent().get(0).getTitle()).isEqualTo("Hoàn thành");
    }
}

