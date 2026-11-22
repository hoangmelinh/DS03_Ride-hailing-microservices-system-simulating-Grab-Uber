package com.goride.matching.adapter.out.persistence;

import com.goride.matching.adapter.out.persistence.entity.MatchingSagaJpaEntity;
import com.goride.matching.adapter.out.persistence.mapper.MatchingPersistenceMapper;
import com.goride.matching.adapter.out.persistence.repository.MatchingSagaSpringDataJpaRepository;
import com.goride.matching.domain.model.MatchingSaga;
import com.goride.matching.domain.model.SagaStatus;
import com.goride.matching.domain.repository.MatchingSagaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class MatchingSagaRepositoryAdapter implements MatchingSagaRepository {

    private final MatchingSagaSpringDataJpaRepository jpaRepository;

    public MatchingSagaRepositoryAdapter(MatchingSagaSpringDataJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<MatchingSaga> findById(UUID id) {
        return jpaRepository.findById(id).map(MatchingPersistenceMapper::toDomain);
    }

    @Override
    public Optional<MatchingSaga> findByTripId(UUID tripId) {
        return jpaRepository.findByTripId(tripId).map(MatchingPersistenceMapper::toDomain);
    }

    @Override
    public List<MatchingSaga> findByStatus(SagaStatus status) {
        return jpaRepository.findByStatus(status).stream()
                .map(MatchingPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<MatchingSaga> findByStatusAndCurrentStep(SagaStatus status, String currentStep) {
        return jpaRepository.findByStatusAndCurrentStep(status, currentStep).stream()
                .map(MatchingPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public MatchingSaga save(MatchingSaga saga) {
        MatchingSagaJpaEntity entity = MatchingPersistenceMapper.toJpa(saga);
        MatchingSagaJpaEntity saved = jpaRepository.save(entity);
        return MatchingPersistenceMapper.toDomain(saved);
    }
}
