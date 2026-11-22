package com.goride.matching.domain.repository;

import com.goride.matching.domain.model.OutboxEvent;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent outboxEvent);

    List<OutboxEvent> findReadyPendingEvents(Instant now);
}
