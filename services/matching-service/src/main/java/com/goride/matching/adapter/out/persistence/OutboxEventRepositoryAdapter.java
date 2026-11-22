package com.goride.matching.adapter.out.persistence;

import com.goride.matching.adapter.out.persistence.entity.OutboxEventJpaEntity;
import com.goride.matching.adapter.out.persistence.mapper.MatchingPersistenceMapper;
import com.goride.matching.adapter.out.persistence.repository.OutboxEventSpringDataJpaRepository;
import com.goride.matching.domain.model.OutboxEvent;
import com.goride.matching.domain.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxEventRepositoryAdapter implements OutboxEventRepository {

    private final OutboxEventSpringDataJpaRepository jpaRepository;

    public OutboxEventRepositoryAdapter(OutboxEventSpringDataJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        OutboxEventJpaEntity entity = MatchingPersistenceMapper.toJpa(outboxEvent);
        OutboxEventJpaEntity saved = jpaRepository.save(entity);
        return MatchingPersistenceMapper.toDomain(saved);
    }

    @Override
    public List<OutboxEvent> findReadyPendingEvents(Instant now) {
        return jpaRepository.findReadyPendingEvents(now).stream()
                .map(MatchingPersistenceMapper::toDomain)
                .toList();
    }
}
