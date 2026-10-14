package com.goride.driver.adapter.out.persistence.repository;

import com.goride.driver.adapter.out.persistence.entity.VehicleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleSpringDataJpaRepository extends JpaRepository<VehicleJpaEntity, UUID> {

    Optional<VehicleJpaEntity> findByPlateNumber(String plateNumber);

    List<VehicleJpaEntity> findByDriverId(UUID driverId);
}
