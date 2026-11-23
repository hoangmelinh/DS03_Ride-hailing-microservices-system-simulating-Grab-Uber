package com.goride.payment.infrastructure.messaging.consumer;

import com.goride.payment.domain.model.DlqMessageStatus;
import com.goride.payment.infrastructure.persistence.entity.DeadLetterMessageEntity;
import com.goride.payment.infrastructure.persistence.repository.PaymentDeadLetterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer for Dead Letter Queue (DLQ) messages routed to payment.dead-letter.queue.
 * Persists failed messages to dead_letter_messages table for auditing and re-drive.
 */
@Component
public class PaymentDeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentDeadLetterConsumer.class);

    private final PaymentDeadLetterRepository deadLetterRepository;
    private final String dlqName;
    private final String exchangeName;
    private final String dlqRoutingKey;

    public PaymentDeadLetterConsumer(
            PaymentDeadLetterRepository deadLetterRepository,
            @Value("${goride.rabbitmq.queues.dlq:payment.dead-letter.queue}") String dlqName,
            @Value("${goride.rabbitmq.dlx:goride.dead-letter.exchange}") String exchangeName,
            @Value("${goride.rabbitmq.routing-keys.dlq:payment.dead-letter}") String dlqRoutingKey) {
        this.deadLetterRepository = deadLetterRepository;
        this.dlqName = dlqName;
        this.exchangeName = exchangeName;
        this.dlqRoutingKey = dlqRoutingKey;
    }

    @RabbitListener(queues = "${goride.rabbitmq.queues.dlq:payment.dead-letter.queue}")
    public void onDeadLetterMessage(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        MessageProperties props = message.getMessageProperties();
        Map<String, Object> headers = props != null ? props.getHeaders() : Map.of();

        String exceptionClass = extractHeaderString(headers, "x-exception-stacktrace", "AmqpListenerExecutionFailedException");
        if (exceptionClass.length() > 255) {
            exceptionClass = exceptionClass.substring(0, 255);
        }
        String exceptionMessage = extractHeaderString(headers, "x-exception-message", "Message rejected after max retries");
        String origExchange = extractHeaderString(headers, "x-original-exchange", exchangeName);
        String origRoutingKey = extractHeaderString(headers, "x-original-routing-key", dlqRoutingKey);
        UUID correlationId = parseCorrelationId(headers.get("correlationId"));

        DeadLetterMessageEntity entity = new DeadLetterMessageEntity();
        entity.setQueueName(dlqName);
        entity.setExchangeName(origExchange);
        entity.setRoutingKey(origRoutingKey);
        entity.setPayload(isValidJson(payload) ? payload : "{\"raw\":\"" + payload.replace("\"", "'") + "\"}");
        entity.setCorrelationId(correlationId);
        entity.setExceptionClass(exceptionClass);
        entity.setExceptionMessage(exceptionMessage);
        entity.setRetryCount(3);
        entity.setStatus(DlqMessageStatus.DEAD);

        deadLetterRepository.save(entity);
        log.warn("Persisted dead-letter message [id={}, queue={}, routingKey={}] into dead_letter_messages",
                entity.getId(), dlqName, origRoutingKey);
    }

    private String extractHeaderString(Map<String, Object> headers, String key, String defaultVal) {
        Object val = headers.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    private UUID parseCorrelationId(Object val) {
        if (val == null) return null;
        try {
            return UUID.fromString(val.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isValidJson(String str) {
        String trimmed = str.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
}
