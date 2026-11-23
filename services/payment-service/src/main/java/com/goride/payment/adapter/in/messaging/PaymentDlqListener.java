package com.goride.payment.adapter.in.messaging;

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

@Component
public class PaymentDlqListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentDlqListener.class);

    private final PaymentDeadLetterRepository paymentDeadLetterRepository;
    private final String dlqName;
    private final String exchangeName;
    private final String dlqRoutingKey;

    public PaymentDlqListener(
            PaymentDeadLetterRepository paymentDeadLetterRepository,
            @Value("${goride.rabbitmq.queues.dlq:payment.dead-letter.queue}") String dlqName,
            @Value("${goride.rabbitmq.exchange:goride.events}") String exchangeName,
            @Value("${goride.rabbitmq.routing-keys.dlq:payment.dead-letter}") String dlqRoutingKey) {
        this.paymentDeadLetterRepository = paymentDeadLetterRepository;
        this.dlqName = dlqName;
        this.exchangeName = exchangeName;
        this.dlqRoutingKey = dlqRoutingKey;
    }

    @RabbitListener(queues = "${goride.rabbitmq.queues.dlq:payment.dead-letter.queue}")
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

        DeadLetterMessageEntity dlqRecord = new DeadLetterMessageEntity(
                dlqName,
                origExchange,
                origRoutingKey,
                isValidJson(payload) ? payload : "{\"raw\":\"" + payload.replace("\"", "'") + "\"}",
                correlationId,
                exceptionClass,
                exceptionMessage
        );

        paymentDeadLetterRepository.save(dlqRecord);
        log.warn("Saved dead-letter message id={} to dead_letter_messages in goride_payment from routingKey={}",
                dlqRecord.getId(), origRoutingKey);
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
