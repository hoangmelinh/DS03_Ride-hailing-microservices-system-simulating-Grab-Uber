package com.goride.driver.adapter.out.persistence.mapper;

import com.goride.driver.adapter.out.persistence.entity.DriverJpaEntity;
import com.goride.driver.adapter.out.persistence.entity.DriverLocationJpaEntity;
import com.goride.driver.adapter.out.persistence.entity.DriverReservationJpaEntity;
import com.goride.driver.adapter.out.persistence.entity.VehicleJpaEntity;
import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.DriverLocation;
import com.goride.driver.domain.model.DriverReservation;
import com.goride.driver.domain.model.Vehicle;

public final class PersistenceDomainMapper {

    private PersistenceDomainMapper() {
    }

    public static Driver toDomain(DriverJpaEntity entity) {
        if (entity == null) return null;
        return new Driver(
                entity.getId(),
                entity.getUserId(),
                entity.getLicenseNumber(),
                entity.getStatus(),
                entity.getCurrentTripId(),
                entity.getReservedUntil(),
                entity.getVersion() != null ? entity.getVersion() : 0L,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static DriverJpaEntity toJpa(Driver domain) {
        if (domain == null) return null;
        return new DriverJpaEntity(
                domain.getId(),
                domain.getUserId(),
                domain.getLicenseNumber(),
                domain.getStatus(),
                domain.getCurrentTripId(),
                domain.getReservedUntil(),
                domain.getVersion(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }

    public static Vehicle toDomain(VehicleJpaEntity entity) {
        if (entity == null) return null;
        return new Vehicle(
                entity.getId(),
                entity.getDriverId(),
                entity.getPlateNumber(),
                entity.getBrand(),
                entity.getModel(),
                entity.getColor(),
                entity.getCapacity() != null ? entity.getCapacity() : 4,
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static VehicleJpaEntity toJpa(Vehicle domain) {
        if (domain == null) return null;
        return new VehicleJpaEntity(
                domain.getId(),
                domain.getDriverId(),
                domain.getPlateNumber(),
                domain.getBrand(),
                domain.getModel(),
                domain.getColor(),
                (short) domain.getCapacity(),
                domain.getStatus(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }

    public static DriverLocation toDomain(DriverLocationJpaEntity entity) {
        if (entity == null) return null;
        return new DriverLocation(
                entity.getDriverId(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getUpdatedAt()
        );
    }

    public static DriverLocationJpaEntity toJpa(DriverLocation domain) {
        if (domain == null) return null;
        return new DriverLocationJpaEntity(
                domain.getDriverId(),
                domain.getLatitude(),
                domain.getLongitude(),
                domain.getUpdatedAt()
        );
    }

    public static DriverReservation toDomain(DriverReservationJpaEntity entity) {
        if (entity == null) return null;
        return new DriverReservation(
                entity.getId(),
                entity.getDriverId(),
                entity.getTripId(),
                entity.getStatus(),
                entity.getIdempotencyKey(),
                entity.getCorrelationId(),
                entity.getReservedAt(),
                entity.getExpiresAt(),
                entity.getConfirmedAt(),
                entity.getReleasedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static DriverReservationJpaEntity toJpa(DriverReservation domain) {
        if (domain == null) return null;
        return new DriverReservationJpaEntity(
                domain.getId(),
                domain.getDriverId(),
                domain.getTripId(),
                domain.getStatus(),
                domain.getIdempotencyKey(),
                domain.getCorrelationId(),
                domain.getReservedAt(),
                domain.getExpiresAt(),
                domain.getConfirmedAt(),
                domain.getReleasedAt(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}
