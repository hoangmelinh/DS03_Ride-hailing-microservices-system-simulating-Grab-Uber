package com.goride.matching.adapter.out.persistence.repository;

import com.goride.matching.adapter.out.persistence.entity.MatchingAttemptJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MatchingAttemptSpringDataJpaRepository extends JpaRepository<MatchingAttemptJpaEntity, UUID> {

    List<MatchingAttemptJpaEntity> findBySagaIdOrderByAttemptNoAsc(UUID sagaId);
}
