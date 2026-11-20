package com.goride.trip.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.event.EventEnvelope;
import com.goride.trip.domain.model.OutboxStatus;
import com.goride.trip.infrastructure.persistence.entity.OutboxEventEntity;
import com.goride.trip.infrastructure.persistence.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Background worker polling outbox_event table for PENDING events
 * and publishing them onto the RabbitMQ Topic Exchange `goride.events`.
 */
@Component
@ConditionalOnProperty(name = "goride.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventPublisherScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisherScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${goride.rabbitmq.exchange:goride.events}")
    private String exchangeName;

    @Value("${goride.outbox.publisher.max-retries:3}")
    private int maxRetries;

    public OutboxEventPublisherScheduler(OutboxEventRepository outboxEventRepository,
                                         RabbitTemplate rabbitTemplate,
                                         ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${goride.outbox.publisher.fixed-delay-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> pendingEvents = outboxEventRepository.findTop50ByStatusOrderByOccurredAtAsc(OutboxStatus.PENDING);
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox event(s) to publish", pendingEvents.size());

        for (OutboxEventEntity event : pendingEvents) {
            publishSingleEvent(event);
        }
    }

    private void publishSingleEvent(OutboxEventEntity event) {
        try {
            JsonNode payloadNode = objectMapper.readTree(event.getPayload());
            EventEnvelope<JsonNode> envelope = new EventEnvelope<>(
                    event.getId(),
                    event.getEventType(),
                    event.getEventVersion(),
                    event.getCorrelationId(),
                    event.getCausationId(),
                    event.getAggregateId().toString(),
                    "trip-service",
                    event.getOccurredAt(),
                    payloadNode
            );

            rabbitTemplate.convertAndSend(exchangeName, event.getEventType(), envelope);

            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            outboxEventRepository.save(event);

            log.info("Published outbox event [id={}, type={}, aggregateId={}, routingKey={}] to exchange '{}'",
                    event.getId(), event.getEventType(), event.getAggregateId(), event.getEventType(), exchangeName);

        } catch (Exception e) {
            int retries = event.getRetryCount() != null ? event.getRetryCount() + 1 : 1;
            event.setRetryCount(retries);

            if (retries >= maxRetries) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("Outbox event [id={}, type={}] exceeded max retries ({}), marked as FAILED: {}",
                        event.getId(), event.getEventType(), maxRetries, e.getMessage(), e);
            } else {
                long backoffSeconds = (long) Math.pow(2, retries);
                event.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
                log.warn("Failed to publish outbox event [id={}, type={}]. Retry {}/{} in {}s: {}",
                        event.getId(), event.getEventType(), retries, maxRetries, backoffSeconds, e.getMessage());
            }

            outboxEventRepository.save(event);
        }
    }
}
