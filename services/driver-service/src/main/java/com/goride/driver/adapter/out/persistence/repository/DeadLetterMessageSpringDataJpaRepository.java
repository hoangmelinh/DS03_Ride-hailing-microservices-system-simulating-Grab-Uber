package com.goride.driver.adapter.out.persistence.repository;

import com.goride.driver.adapter.out.persistence.entity.DeadLetterMessageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeadLetterMessageSpringDataJpaRepository extends JpaRepository<DeadLetterMessageJpaEntity, UUID> {

    List<DeadLetterMessageJpaEntity> findByStatus(String status);
}
