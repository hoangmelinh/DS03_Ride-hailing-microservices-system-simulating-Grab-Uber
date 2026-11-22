package com.goride.matching.adapter.out.persistence;

import com.goride.matching.adapter.out.persistence.entity.DeadLetterMessageJpaEntity;
import com.goride.matching.adapter.out.persistence.mapper.MatchingPersistenceMapper;
import com.goride.matching.adapter.out.persistence.repository.DeadLetterMessageSpringDataJpaRepository;
import com.goride.matching.domain.model.DeadLetterMessage;
import com.goride.matching.domain.repository.DeadLetterMessageRepository;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterMessageRepositoryAdapter implements DeadLetterMessageRepository {

    private final DeadLetterMessageSpringDataJpaRepository jpaRepository;

    public DeadLetterMessageRepositoryAdapter(DeadLetterMessageSpringDataJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public DeadLetterMessage save(DeadLetterMessage deadLetterMessage) {
        DeadLetterMessageJpaEntity entity = MatchingPersistenceMapper.toJpa(deadLetterMessage);
        DeadLetterMessageJpaEntity saved = jpaRepository.save(entity);
        return MatchingPersistenceMapper.toDomain(saved);
    }
}
