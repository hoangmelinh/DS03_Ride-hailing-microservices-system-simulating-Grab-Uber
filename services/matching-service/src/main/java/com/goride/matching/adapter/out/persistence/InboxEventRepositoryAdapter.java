package com.goride.matching.adapter.out.persistence;

import com.goride.matching.adapter.out.persistence.entity.InboxEventJpaEntity;
import com.goride.matching.adapter.out.persistence.mapper.MatchingPersistenceMapper;
import com.goride.matching.adapter.out.persistence.repository.InboxEventSpringDataJpaRepository;
import com.goride.matching.domain.model.InboxEvent;
import com.goride.matching.domain.repository.InboxEventRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InboxEventRepositoryAdapter implements InboxEventRepository {

    private final InboxEventSpringDataJpaRepository jpaRepository;

    public InboxEventRepositoryAdapter(InboxEventSpringDataJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean existsByEventId(UUID eventId) {
        return jpaRepository.existsById(eventId);
    }

    @Override
    public InboxEvent save(InboxEvent inboxEvent) {
        InboxEventJpaEntity entity = MatchingPersistenceMapper.toJpa(inboxEvent);
        InboxEventJpaEntity saved = jpaRepository.save(entity);
        return MatchingPersistenceMapper.toDomain(saved);
    }
}
