package com.goride.matching.adapter.in.messaging;

import com.goride.matching.domain.model.DeadLetterMessage;
import com.goride.matching.domain.repository.DeadLetterMessageRepository;
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
class MatchingDlqListenerTest {

    @Mock
    private DeadLetterMessageRepository deadLetterMessageRepository;

    @Test
    @DisplayName("DLQ Listener should persist dead-letter message into dead_letter_messages")
    void onDeadLetterMessage_ShouldSaveDeadLetterRecord() {
        MatchingDlqListener listener = new MatchingDlqListener(
                deadLetterMessageRepository, "matching.dead-letter.queue", "goride.events", "matching.dead-letter");

        MessageProperties props = new MessageProperties();
        props.setHeader("x-exception-message", "Simulated RabbitMQ processing error");
        Message message = new Message("{\"tripId\":\"123\"}".getBytes(StandardCharsets.UTF_8), props);

        listener.onDeadLetterMessage(message);

        ArgumentCaptor<DeadLetterMessage> captor = ArgumentCaptor.forClass(DeadLetterMessage.class);
        verify(deadLetterMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getQueueName()).isEqualTo("matching.dead-letter.queue");
        assertThat(captor.getValue().getStatus()).isEqualTo("DEAD");
        assertThat(captor.getValue().getExceptionMessage()).isEqualTo("Simulated RabbitMQ processing error");
    }
}
