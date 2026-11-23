package com.goride.payment.infrastructure.messaging.publisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.event.EventEnvelope;
import com.goride.payment.domain.model.OutboxStatus;
import com.goride.payment.infrastructure.persistence.entity.OutboxEventEntity;
import com.goride.payment.infrastructure.persistence.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Background worker polling outbox_event table for PENDING payment events
 * and publishing them onto the RabbitMQ Topic Exchange `goride.events`.
 */
@Component
@ConditionalOnProperty(name = "goride.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentOutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${goride.rabbitmq.exchange:goride.events}")
    private String exchangeName;

    @Value("${goride.outbox.publisher.max-retries:3}")
    private int maxRetries;

    @Value("${goride.outbox.publisher.await-confirm:true}")
    private boolean awaitConfirm;

    @Value("${goride.outbox.publisher.confirm-timeout-ms:2000}")
    private long confirmTimeoutMs;

    public PaymentOutboxPublisher(OutboxEventRepository outboxEventRepository,
                                  RabbitTemplate rabbitTemplate,
                                  ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${goride.outbox.publisher.fixed-delay-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        Instant now = Instant.now();
        List<OutboxEventEntity> pendingEvents = outboxEventRepository
                .findPendingEventsToPublish(OutboxStatus.PENDING, now, PageRequest.of(0, 50));

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending payment outbox event(s) to publish", pendingEvents.size());

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
                    "payment-service",
                    event.getOccurredAt(),
                    payloadNode
            );

            CorrelationData correlationData = new CorrelationData(event.getId().toString());
            rabbitTemplate.convertAndSend(exchangeName, event.getEventType(), envelope, correlationData);

            if (awaitConfirm && correlationData.getFuture() != null) {
                try {
                    CorrelationData.Confirm confirm = correlationData.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
                    if (confirm != null && !confirm.isAck()) {
                        throw new AmqpException("RabbitMQ broker returned NACK for outbox event id=" + event.getId()
                                + ", reason=" + confirm.getReason());
                    }
                } catch (TimeoutException te) {
                    throw new AmqpException("Publisher confirm timed out for outbox event id=" + event.getId(), te);
                }
            }

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
                log.error("Payment outbox event [id={}, type={}] exceeded max retries ({}), marked as FAILED: {}",
                        event.getId(), event.getEventType(), maxRetries, e.getMessage(), e);
            } else {
                long backoffSeconds = (long) Math.pow(2, retries);
                event.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
                log.warn("Failed to publish payment outbox event [id={}, type={}]. Retry {}/{} in {}s: {}",
                        event.getId(), event.getEventType(), retries, maxRetries, backoffSeconds, e.getMessage());
            }

            outboxEventRepository.save(event);
        }
    }
}
