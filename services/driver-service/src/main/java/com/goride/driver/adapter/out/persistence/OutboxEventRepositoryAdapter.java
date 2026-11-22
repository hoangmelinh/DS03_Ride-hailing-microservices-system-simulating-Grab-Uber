package com.goride.driver.adapter.out.persistence;

import com.goride.driver.adapter.out.persistence.entity.OutboxEventJpaEntity;
import com.goride.driver.adapter.out.persistence.mapper.PersistenceDomainMapper;
import com.goride.driver.adapter.out.persistence.repository.OutboxEventSpringDataJpaRepository;
import com.goride.driver.domain.model.OutboxEvent;
import com.goride.driver.domain.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class OutboxEventRepositoryAdapter implements OutboxEventRepository {

    private final OutboxEventSpringDataJpaRepository jpaRepository;

    public OutboxEventRepositoryAdapter(OutboxEventSpringDataJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        OutboxEventJpaEntity entity = PersistenceDomainMapper.toJpa(outboxEvent);
        OutboxEventJpaEntity saved = jpaRepository.save(entity);
        return PersistenceDomainMapper.toDomain(saved);
    }

    @Override
    public Optional<OutboxEvent> findById(UUID id) {
        return jpaRepository.findById(id).map(PersistenceDomainMapper::toDomain);
    }
}

