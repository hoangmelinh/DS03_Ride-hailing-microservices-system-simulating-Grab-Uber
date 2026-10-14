package com.goride.driver.adapter.out.persistence;

import com.goride.driver.adapter.out.persistence.entity.VehicleJpaEntity;
import com.goride.driver.adapter.out.persistence.mapper.PersistenceDomainMapper;
import com.goride.driver.adapter.out.persistence.repository.VehicleSpringDataJpaRepository;
import com.goride.driver.domain.model.Vehicle;
import com.goride.driver.domain.repository.VehicleRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class VehicleRepositoryAdapter implements VehicleRepository {

    private final VehicleSpringDataJpaRepository jpaRepository;

    public VehicleRepositoryAdapter(VehicleSpringDataJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Vehicle> findById(UUID id) {
        return jpaRepository.findById(id).map(PersistenceDomainMapper::toDomain);
    }

    @Override
    public Optional<Vehicle> findByPlateNumber(String plateNumber) {
        return jpaRepository.findByPlateNumber(plateNumber).map(PersistenceDomainMapper::toDomain);
    }

    @Override
    public List<Vehicle> findByDriverId(UUID driverId) {
        return jpaRepository.findByDriverId(driverId)
                .stream()
                .map(PersistenceDomainMapper::toDomain)
                .toList();
    }

    @Override
    public Vehicle save(Vehicle vehicle) {
        VehicleJpaEntity entity = PersistenceDomainMapper.toJpa(vehicle);
        VehicleJpaEntity saved = jpaRepository.save(entity);
        return PersistenceDomainMapper.toDomain(saved);
    }
}
