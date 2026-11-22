package com.goride.payment.infrastructure.persistence.repository;

import com.goride.payment.domain.model.PaymentStatus;
import com.goride.payment.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByTripId(UUID tripId);

    boolean existsByTripId(UUID tripId);

    Page<PaymentEntity> findByPassengerId(UUID passengerId, Pageable pageable);

    Page<PaymentEntity> findByPassengerIdOrderByCreatedAtDesc(UUID passengerId, Pageable pageable);

    Page<PaymentEntity> findByStatus(PaymentStatus status, Pageable pageable);

    Optional<PaymentEntity> findByTripIdAndPassengerId(UUID tripId, UUID passengerId);
}
