package com.goride.matching.domain.repository;

import com.goride.matching.domain.model.InboxEvent;

import java.util.UUID;

public interface InboxEventRepository {

    boolean existsByEventId(UUID eventId);

    InboxEvent save(InboxEvent inboxEvent);
}
