package com.goride.matching.adapter.in.messaging;

import com.goride.matching.domain.model.DeadLetterMessage;
import com.goride.matching.domain.repository.DeadLetterMessageRepository;
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

@Component
public class MatchingDlqListener {

    private static final Logger log = LoggerFactory.getLogger(MatchingDlqListener.class);

    private final DeadLetterMessageRepository deadLetterMessageRepository;
    private final String dlqName;
    private final String exchangeName;
    private final String dlqRoutingKey;

    public MatchingDlqListener(
            DeadLetterMessageRepository deadLetterMessageRepository,
            @Value("${goride.rabbitmq.queues.dlq:matching.dead-letter.queue}") String dlqName,
            @Value("${goride.rabbitmq.exchange:goride.events}") String exchangeName,
            @Value("${goride.rabbitmq.routing-keys.dlq:matching.dead-letter}") String dlqRoutingKey) {
        this.deadLetterMessageRepository = deadLetterMessageRepository;
        this.dlqName = dlqName;
        this.exchangeName = exchangeName;
        this.dlqRoutingKey = dlqRoutingKey;
    }

    @RabbitListener(queues = "${goride.rabbitmq.queues.dlq:matching.dead-letter.queue}")
    public void onDeadLetterMessage(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        MessageProperties props = message.getMessageProperties();
        Map<String, Object> headers = props != null ? props.getHeaders() : Map.of();

        String exceptionClass = extractHeaderString(headers, "x-exception-stacktrace", "AmqpListenerException");
        if (exceptionClass.length() > 255) {
            exceptionClass = exceptionClass.substring(0, 255);
        }
        String exceptionMessage = extractHeaderString(headers, "x-exception-message", "Message routed to DLQ after max retries");
        String origExchange = extractHeaderString(headers, "x-original-exchange", exchangeName);
        String origRoutingKey = extractHeaderString(headers, "x-original-routing-key", dlqRoutingKey);
        UUID correlationId = parseCorrelationId(headers.get("correlationId"));

        DeadLetterMessage dlqRecord = DeadLetterMessage.create(
                dlqName,
                origExchange,
                origRoutingKey,
                isValidJson(payload) ? payload : "{\"raw\":\"" + payload.replace("\"", "'") + "\"}",
                correlationId,
                exceptionClass,
                exceptionMessage,
                3
        );

        deadLetterMessageRepository.save(dlqRecord);
        log.warn("Saved dead-letter message id={} to dead_letter_messages from routingKey={}", dlqRecord.getId(), origRoutingKey);
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
