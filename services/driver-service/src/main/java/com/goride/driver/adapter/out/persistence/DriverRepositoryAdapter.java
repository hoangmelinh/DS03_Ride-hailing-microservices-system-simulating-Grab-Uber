package com.goride.driver.adapter.out.persistence;

import com.goride.driver.adapter.out.persistence.entity.DriverJpaEntity;
import com.goride.driver.adapter.out.persistence.mapper.PersistenceDomainMapper;
import com.goride.driver.adapter.out.persistence.repository.DriverSpringDataJpaRepository;
import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.DriverStatus;
import com.goride.driver.domain.repository.DriverRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DriverRepositoryAdapter implements DriverRepository {

    private final DriverSpringDataJpaRepository jpaRepository;

    public DriverRepositoryAdapter(DriverSpringDataJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Driver> findById(UUID id) {
        return jpaRepository.findById(id).map(PersistenceDomainMapper::toDomain);
    }

    @Override
    public Optional<Driver> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).map(PersistenceDomainMapper::toDomain);
    }

    @Override
    public List<Driver> findByStatus(DriverStatus status) {
        return jpaRepository.findByStatus(status)
                .stream()
                .map(PersistenceDomainMapper::toDomain)
                .toList();
    }

    @Override
    public Driver save(Driver driver) {
        DriverJpaEntity entity = PersistenceDomainMapper.toJpa(driver);
        DriverJpaEntity saved = jpaRepository.save(entity);
        return PersistenceDomainMapper.toDomain(saved);
    }

    @Override
    public boolean atomicReserve(UUID driverId, UUID tripId, Instant expiresAt) {
        int affectedRows = jpaRepository.atomicReserve(driverId, tripId, expiresAt, Instant.now());
        return affectedRows == 1;
    }

    @Override
    public boolean atomicRelease(UUID driverId, UUID tripId) {
        int affectedRows = jpaRepository.atomicRelease(driverId, tripId, Instant.now());
        return affectedRows == 1;
    }
}
