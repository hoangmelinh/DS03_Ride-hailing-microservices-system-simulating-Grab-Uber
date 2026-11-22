package com.goride.matching.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.event.EventEnvelope;
import com.goride.matching.domain.model.OutboxEvent;
import com.goride.matching.domain.repository.OutboxEventRepository;
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

@Component
@ConditionalOnProperty(name = "goride.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class MatchingOutboxPublisherScheduler {

    private static final Logger log = LoggerFactory.getLogger(MatchingOutboxPublisherScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchangeName;
    private final int maxRetries;

    public MatchingOutboxPublisherScheduler(
            OutboxEventRepository outboxEventRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${goride.rabbitmq.exchange:goride.events}") String exchangeName,
            @Value("${goride.outbox.publisher.max-retries:3}") int maxRetries) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.exchangeName = exchangeName;
        this.maxRetries = maxRetries;
    }

    @Scheduled(fixedDelayString = "${goride.outbox.publisher.fixed-delay-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findReadyPendingEvents(Instant.now());
        if (pendingEvents.isEmpty()) {
            return;
        }
        for (OutboxEvent event : pendingEvents) {
            publishSingleEvent(event);
        }
    }

    private void publishSingleEvent(OutboxEvent event) {
        try {
            JsonNode payloadNode = objectMapper.readTree(event.getPayload());
            EventEnvelope<JsonNode> envelope = new EventEnvelope<>(
                    event.getId(),
                    event.getEventType(),
                    event.getEventVersion(),
                    event.getCorrelationId(),
                    event.getCausationId(),
                    event.getAggregateId().toString(),
                    "matching-service",
                    event.getOccurredAt(),
                    payloadNode
            );

            rabbitTemplate.convertAndSend(exchangeName, event.getEventType(), envelope);
            event.markPublished();
            outboxEventRepository.save(event);

            log.info("Published matching outbox event id={}, type={}, routingKey={}",
                    event.getId(), event.getEventType(), event.getEventType());
        } catch (Exception ex) {
            event.markRetryOrFailed(maxRetries);
            outboxEventRepository.save(event);
            log.warn("Failed to publish matching outbox event id={} (retry={}/{}): {}",
                    event.getId(), event.getRetryCount(), maxRetries, ex.getMessage());
        }
    }
}
