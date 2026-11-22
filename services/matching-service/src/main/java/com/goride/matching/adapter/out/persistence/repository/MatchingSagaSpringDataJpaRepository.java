package com.goride.matching.adapter.out.persistence.repository;

import com.goride.matching.adapter.out.persistence.entity.MatchingSagaJpaEntity;
import com.goride.matching.domain.model.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchingSagaSpringDataJpaRepository extends JpaRepository<MatchingSagaJpaEntity, UUID> {

    Optional<MatchingSagaJpaEntity> findByTripId(UUID tripId);

    List<MatchingSagaJpaEntity> findByStatus(SagaStatus status);

    List<MatchingSagaJpaEntity> findByStatusAndCurrentStep(SagaStatus status, String currentStep);
}
