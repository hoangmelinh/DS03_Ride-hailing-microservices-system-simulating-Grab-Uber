package com.goride.notification.infrastructure.messaging.consumer;

import com.goride.notification.infrastructure.persistence.entity.DeadLetterMessageEntity;
import com.goride.notification.infrastructure.persistence.repository.NotificationDeadLetterRepository;
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
class NotificationDeadLetterConsumerTest {

    @Mock
    private NotificationDeadLetterRepository deadLetterRepository;

    @Test
    @DisplayName("DLQ Consumer: Successfully persists dead letter message into dead_letter_messages table")
    void onDeadLetterMessage_ShouldSaveDeadLetterRecord() {
        NotificationDeadLetterConsumer consumer = new NotificationDeadLetterConsumer(
                deadLetterRepository,
                "notification.dead-letter.queue",
                "goride.dead-letter.exchange",
                "notification.dead-letter"
        );

        MessageProperties props = new MessageProperties();
        props.setHeader("x-exception-message", "Notification delivery failed after 3 retries");
        props.setHeader("x-original-routing-key", "payment.succeeded");
        Message message = new Message("{\"passengerId\":\"xyz-456\"}".getBytes(StandardCharsets.UTF_8), props);

        consumer.onDeadLetterMessage(message);

        ArgumentCaptor<DeadLetterMessageEntity> captor = ArgumentCaptor.forClass(DeadLetterMessageEntity.class);
        verify(deadLetterRepository).save(captor.capture());
        DeadLetterMessageEntity saved = captor.getValue();
        assertThat(saved.getQueueName()).isEqualTo("notification.dead-letter.queue");
        assertThat(saved.getExceptionMessage()).isEqualTo("Notification delivery failed after 3 retries");
        assertThat(saved.getPayload()).contains("xyz-456");
    }
}
