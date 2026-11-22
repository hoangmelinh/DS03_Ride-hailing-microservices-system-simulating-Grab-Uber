package com.goride.matching.adapter.out.persistence.repository;

import com.goride.matching.adapter.out.persistence.entity.InboxEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InboxEventSpringDataJpaRepository extends JpaRepository<InboxEventJpaEntity, UUID> {
}
