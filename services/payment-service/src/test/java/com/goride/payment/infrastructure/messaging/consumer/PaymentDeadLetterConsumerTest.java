package com.goride.payment.infrastructure.messaging.consumer;

import com.goride.payment.infrastructure.persistence.entity.DeadLetterMessageEntity;
import com.goride.payment.infrastructure.persistence.repository.PaymentDeadLetterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentDeadLetterConsumerTest {

    @Mock
    private PaymentDeadLetterRepository deadLetterRepository;

    @Test
    @DisplayName("DLQ Consumer: Successfully persists dead letter message into dead_letter_messages table")
    void onDeadLetterMessage_ShouldSaveDeadLetterRecord() {
        PaymentDeadLetterConsumer consumer = new PaymentDeadLetterConsumer(
                deadLetterRepository,
                "payment.dead-letter.queue",
                "goride.dead-letter.exchange",
                "payment.dead-letter"
        );

        MessageProperties props = new MessageProperties();
        props.setHeader("x-exception-message", "Payment processing failed after 3 retries");
        props.setHeader("x-original-routing-key", "trip.completed");
        Message message = new Message("{\"tripId\":\"abc-123\"}".getBytes(StandardCharsets.UTF_8), props);

        consumer.onDeadLetterMessage(message);

        ArgumentCaptor<DeadLetterMessageEntity> captor = ArgumentCaptor.forClass(DeadLetterMessageEntity.class);
        verify(deadLetterRepository).save(captor.capture());
        DeadLetterMessageEntity saved = captor.getValue();
        assertThat(saved.getQueueName()).isEqualTo("payment.dead-letter.queue");
        assertThat(saved.getExceptionMessage()).isEqualTo("Payment processing failed after 3 retries");
        assertThat(saved.getPayload()).contains("abc-123");
    }
}
