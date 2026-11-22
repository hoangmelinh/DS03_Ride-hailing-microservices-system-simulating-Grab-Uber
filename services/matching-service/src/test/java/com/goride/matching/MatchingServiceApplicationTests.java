package com.goride.matching;

import com.goride.matching.adapter.out.persistence.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "goride.outbox.publisher.enabled=false",
        "goride.matching.recovery.enabled=false"
})
class MatchingServiceApplicationTests {

    @MockBean
    private MatchingSagaSpringDataJpaRepository matchingSagaSpringDataJpaRepository;

    @MockBean
    private MatchingAttemptSpringDataJpaRepository matchingAttemptSpringDataJpaRepository;

    @MockBean
    private OutboxEventSpringDataJpaRepository outboxEventSpringDataJpaRepository;

    @MockBean
    private InboxEventSpringDataJpaRepository inboxEventSpringDataJpaRepository;

    @MockBean
    private DeadLetterMessageSpringDataJpaRepository deadLetterMessageSpringDataJpaRepository;

    @MockBean
    private ConnectionFactory connectionFactory;

    @Test
    void contextLoads() {
    }
}
