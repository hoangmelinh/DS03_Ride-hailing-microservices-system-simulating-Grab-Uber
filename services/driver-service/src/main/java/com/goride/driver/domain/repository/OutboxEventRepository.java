package com.goride.driver.domain.repository;

import com.goride.driver.domain.model.OutboxEvent;

import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent outboxEvent);

    Optional<OutboxEvent> findById(UUID id);
}

