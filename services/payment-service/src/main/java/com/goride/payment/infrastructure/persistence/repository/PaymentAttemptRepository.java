package com.goride.payment.infrastructure.persistence.repository;

import com.goride.payment.domain.model.PaymentAttemptStatus;
import com.goride.payment.infrastructure.persistence.entity.PaymentAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, UUID> {

    List<PaymentAttemptEntity> findByPayment_IdOrderByAttemptNoAsc(UUID paymentId);

    Optional<PaymentAttemptEntity> findByPayment_IdAndAttemptNo(UUID paymentId, Integer attemptNo);

    List<PaymentAttemptEntity> findByStatus(PaymentAttemptStatus status);
}
