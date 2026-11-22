package com.goride.matching.adapter.out.persistence;

import com.goride.matching.adapter.out.persistence.entity.MatchingAttemptJpaEntity;
import com.goride.matching.adapter.out.persistence.mapper.MatchingPersistenceMapper;
import com.goride.matching.adapter.out.persistence.repository.MatchingAttemptSpringDataJpaRepository;
import com.goride.matching.domain.model.MatchingAttempt;
import com.goride.matching.domain.repository.MatchingAttemptRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class MatchingAttemptRepositoryAdapter implements MatchingAttemptRepository {

    private final MatchingAttemptSpringDataJpaRepository jpaRepository;

    public MatchingAttemptRepositoryAdapter(MatchingAttemptSpringDataJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public MatchingAttempt save(MatchingAttempt attempt) {
        MatchingAttemptJpaEntity entity = MatchingPersistenceMapper.toJpa(attempt);
        MatchingAttemptJpaEntity saved = jpaRepository.save(entity);
        return MatchingPersistenceMapper.toDomain(saved);
    }

    @Override
    public List<MatchingAttempt> findBySagaId(UUID sagaId) {
        return jpaRepository.findBySagaIdOrderByAttemptNoAsc(sagaId).stream()
                .map(MatchingPersistenceMapper::toDomain)
                .toList();
    }
}
