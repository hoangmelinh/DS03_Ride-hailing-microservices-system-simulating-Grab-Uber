package com.goride.trip;

import com.goride.trip.infrastructure.persistence.repository.IdempotencyRecordRepository;
import com.goride.trip.infrastructure.persistence.repository.InboxEventRepository;
import com.goride.trip.infrastructure.persistence.repository.OutboxEventRepository;
import com.goride.trip.infrastructure.persistence.repository.TripDeadLetterRepository;
import com.goride.trip.infrastructure.persistence.repository.TripRepository;
import com.goride.trip.infrastructure.persistence.repository.TripStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class TripServiceApplicationTests {

    @MockBean
    private TripRepository tripRepository;

    @MockBean
    private TripStatusHistoryRepository tripStatusHistoryRepository;

    @MockBean
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private InboxEventRepository inboxEventRepository;

    @MockBean
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @MockBean
    private TripDeadLetterRepository tripDeadLetterRepository;

    @Test
    void contextLoads() {
    }
}
