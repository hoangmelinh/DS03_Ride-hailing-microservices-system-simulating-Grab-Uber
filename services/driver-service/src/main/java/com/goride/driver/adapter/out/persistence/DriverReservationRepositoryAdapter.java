package com.goride.driver.adapter.out.persistence;

import com.goride.driver.adapter.out.persistence.entity.DriverReservationJpaEntity;
import com.goride.driver.adapter.out.persistence.mapper.PersistenceDomainMapper;
import com.goride.driver.adapter.out.persistence.repository.DriverReservationSpringDataJpaRepository;
import com.goride.driver.domain.model.DriverReservation;
import com.goride.driver.domain.repository.DriverReservationRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class DriverReservationRepositoryAdapter implements DriverReservationRepository {

    private final DriverReservationSpringDataJpaRepository jpaRepository;

    public DriverReservationRepositoryAdapter(DriverReservationSpringDataJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<DriverReservation> findById(UUID id) {
        return jpaRepository.findById(id).map(PersistenceDomainMapper::toDomain);
    }

    @Override
    public Optional<DriverReservation> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(PersistenceDomainMapper::toDomain);
    }

    @Override
    public Optional<DriverReservation> findActiveByDriverId(UUID driverId) {
        return jpaRepository.findActiveByDriverId(driverId).map(PersistenceDomainMapper::toDomain);
    }

    @Override
    public Optional<DriverReservation> findActiveByTripId(UUID tripId) {
        return jpaRepository.findActiveByTripId(tripId).map(PersistenceDomainMapper::toDomain);
    }

    @Override
    public DriverReservation save(DriverReservation reservation) {
        DriverReservationJpaEntity entity = PersistenceDomainMapper.toJpa(reservation);
        DriverReservationJpaEntity saved = jpaRepository.save(entity);
        return PersistenceDomainMapper.toDomain(saved);
    }
}
