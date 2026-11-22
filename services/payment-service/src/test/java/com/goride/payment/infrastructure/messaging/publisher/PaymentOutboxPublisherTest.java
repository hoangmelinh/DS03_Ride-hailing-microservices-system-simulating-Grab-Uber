package com.goride.payment.infrastructure.messaging.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.goride.payment.domain.model.OutboxStatus;
import com.goride.payment.infrastructure.persistence.entity.OutboxEventEntity;
import com.goride.payment.infrastructure.persistence.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper;
    private PaymentOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        publisher = new PaymentOutboxPublisher(outboxEventRepository, rabbitTemplate, objectMapper);
        ReflectionTestUtils.setField(publisher, "exchangeName", "goride.events");
        ReflectionTestUtils.setField(publisher, "maxRetries", 3);
    }

    @Test
    @DisplayName("publishPendingEvents: Successfully publishes pending event and marks status as PUBLISHED")
    void publishPendingEvents_Success() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        OutboxEventEntity event = new OutboxEventEntity(
                eventId,
                "PAYMENT",
                aggregateId,
                "payment.succeeded",
                "{\"paymentId\":\"" + aggregateId + "\",\"amount\":50000}",
                correlationId,
                null,
                Instant.now()
        );

        when(outboxEventRepository.findPendingEventsToPublish(eq(OutboxStatus.PENDING), any(), any()))
                .thenReturn(List.of(event));

        publisher.publishPendingEvents();

        verify(rabbitTemplate).convertAndSend(eq("goride.events"), eq("payment.succeeded"), any(Object.class));

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEventEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(saved.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("publishPendingEvents: Increments retryCount and sets nextRetryAt on RabbitMQ failure")
    void publishPendingEvents_RetryOnFailure() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();

        OutboxEventEntity event = new OutboxEventEntity(
                eventId,
                "PAYMENT",
                aggregateId,
                "payment.succeeded",
                "{\"paymentId\":\"" + aggregateId + "\"}",
                UUID.randomUUID(),
                null,
                Instant.now()
        );
        event.setRetryCount(0);

        when(outboxEventRepository.findPendingEventsToPublish(eq(OutboxStatus.PENDING), any(), any()))
                .thenReturn(List.of(event));
        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(rabbitTemplate).convertAndSend(any(), any(), any(Object.class));

        publisher.publishPendingEvents();

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEventEntity saved = captor.getValue();
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getNextRetryAt()).isNotNull();
    }

    @Test
    @DisplayName("publishPendingEvents: Marks status as FAILED when exceeding max retries")
    void publishPendingEvents_ExceedMaxRetries_MarksFailed() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();

        OutboxEventEntity event = new OutboxEventEntity(
                eventId,
                "PAYMENT",
                aggregateId,
                "payment.succeeded",
                "{\"paymentId\":\"" + aggregateId + "\"}",
                UUID.randomUUID(),
                null,
                Instant.now()
        );
        event.setRetryCount(2); // with maxRetries = 3, next retry reaches 3 -> FAILED

        when(outboxEventRepository.findPendingEventsToPublish(eq(OutboxStatus.PENDING), any(), any()))
                .thenReturn(List.of(event));
        doThrow(new RuntimeException("Broker unavailable"))
                .when(rabbitTemplate).convertAndSend(any(), any(), any(Object.class));

        publisher.publishPendingEvents();

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEventEntity saved = captor.getValue();
        assertThat(saved.getRetryCount()).isEqualTo(3);
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }
}
