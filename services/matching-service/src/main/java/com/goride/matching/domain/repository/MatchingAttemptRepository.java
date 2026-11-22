package com.goride.matching.domain.repository;

import com.goride.matching.domain.model.MatchingAttempt;

import java.util.List;
import java.util.UUID;

public interface MatchingAttemptRepository {

    MatchingAttempt save(MatchingAttempt attempt);

    List<MatchingAttempt> findBySagaId(UUID sagaId);
}
