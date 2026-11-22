package com.goride.driver.adapter.out.persistence.repository;

import com.goride.driver.adapter.out.persistence.entity.DriverLocationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DriverLocationSpringDataJpaRepository extends JpaRepository<DriverLocationJpaEntity, UUID> {

    List<DriverLocationJpaEntity> findByDriverIdIn(Collection<UUID> driverIds);
}
