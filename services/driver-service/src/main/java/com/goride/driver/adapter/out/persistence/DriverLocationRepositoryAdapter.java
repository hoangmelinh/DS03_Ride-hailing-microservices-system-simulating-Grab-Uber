package com.goride.driver.adapter.out.persistence;

import com.goride.driver.adapter.out.persistence.entity.DriverLocationJpaEntity;
import com.goride.driver.adapter.out.persistence.mapper.PersistenceDomainMapper;
import com.goride.driver.adapter.out.persistence.repository.DriverLocationSpringDataJpaRepository;
import com.goride.driver.domain.model.DriverLocation;
import com.goride.driver.domain.repository.DriverLocationRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class DriverLocationRepositoryAdapter implements DriverLocationRepository {

    private final DriverLocationSpringDataJpaRepository jpaRepository;

    public DriverLocationRepositoryAdapter(DriverLocationSpringDataJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<DriverLocation> findByDriverId(UUID driverId) {
        return jpaRepository.findById(driverId).map(PersistenceDomainMapper::toDomain);
    }

    @Override
    public DriverLocation save(DriverLocation driverLocation) {
        DriverLocationJpaEntity entity = PersistenceDomainMapper.toJpa(driverLocation);
        DriverLocationJpaEntity saved = jpaRepository.save(entity);
        return PersistenceDomainMapper.toDomain(saved);
    }
}
