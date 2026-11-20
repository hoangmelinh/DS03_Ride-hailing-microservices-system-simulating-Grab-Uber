package com.goride.trip.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.goride.trip.domain.model.OutboxStatus;
import com.goride.trip.infrastructure.persistence.entity.OutboxEventEntity;
import com.goride.trip.infrastructure.persistence.repository.OutboxEventRepository;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherSchedulerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper;
    private OutboxEventPublisherScheduler scheduler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        scheduler = new OutboxEventPublisherScheduler(outboxEventRepository, rabbitTemplate, objectMapper);
        ReflectionTestUtils.setField(scheduler, "exchangeName", "goride.events");
        ReflectionTestUtils.setField(scheduler, "maxRetries", 3);
    }

    @Test
    @DisplayName("publishPendingEvents: Successfully publishes pending event and marks status as PUBLISHED")
    void publishPendingEvents_Success() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        OutboxEventEntity event = new OutboxEventEntity(
                eventId,
                "TRIP",
                aggregateId,
                "trip.created",
                "{\"tripId\":\"" + aggregateId + "\",\"status\":\"REQUESTED\"}",
                correlationId,
                null,
                Instant.now()
        );

        when(outboxEventRepository.findTop50ByStatusOrderByOccurredAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));

        scheduler.publishPendingEvents();

        // Verify sent to RabbitMQ exchange
        verify(rabbitTemplate).convertAndSend(eq("goride.events"), eq("trip.created"), any(Object.class));

        // Verify event updated to PUBLISHED
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEventEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(saved.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("publishPendingEvents: Increments retryCount on RabbitMQ failure")
    void publishPendingEvents_RetryOnFailure() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();

        OutboxEventEntity event = new OutboxEventEntity(
                eventId,
                "TRIP",
                aggregateId,
                "trip.created",
                "{\"tripId\":\"" + aggregateId + "\"}",
                UUID.randomUUID(),
                null,
                Instant.now()
        );
        event.setRetryCount(0);

        when(outboxEventRepository.findTop50ByStatusOrderByOccurredAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));
        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(rabbitTemplate).convertAndSend(any(), any(), any(Object.class));

        scheduler.publishPendingEvents();

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
                "TRIP",
                aggregateId,
                "trip.created",
                "{\"tripId\":\"" + aggregateId + "\"}",
                UUID.randomUUID(),
                null,
                Instant.now()
        );
        event.setRetryCount(2); // with maxRetries = 3, next retry reaches 3 -> FAILED

        when(outboxEventRepository.findTop50ByStatusOrderByOccurredAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));
        doThrow(new RuntimeException("Broker unavailable"))
                .when(rabbitTemplate).convertAndSend(any(), any(), any(Object.class));

        scheduler.publishPendingEvents();

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEventEntity saved = captor.getValue();
        assertThat(saved.getRetryCount()).isEqualTo(3);
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }
}
