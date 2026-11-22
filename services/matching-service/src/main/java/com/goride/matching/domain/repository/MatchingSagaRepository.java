package com.goride.matching.domain.repository;

import com.goride.matching.domain.model.MatchingSaga;
import com.goride.matching.domain.model.SagaStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchingSagaRepository {

    Optional<MatchingSaga> findById(UUID id);

    Optional<MatchingSaga> findByTripId(UUID tripId);

    List<MatchingSaga> findByStatus(SagaStatus status);

    List<MatchingSaga> findByStatusAndCurrentStep(SagaStatus status, String currentStep);

    MatchingSaga save(MatchingSaga saga);
}
