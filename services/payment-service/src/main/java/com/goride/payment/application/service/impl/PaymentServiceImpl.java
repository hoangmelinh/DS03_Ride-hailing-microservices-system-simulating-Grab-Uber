package com.goride.payment.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.event.PaymentCompletedPayload;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.payment.application.dto.request.ProcessPaymentRequest;
import com.goride.payment.application.dto.response.PaymentResponse;
import com.goride.payment.application.service.PaymentService;
import com.goride.payment.domain.model.PaymentAttemptStatus;
import com.goride.payment.domain.model.PaymentStatus;
import com.goride.payment.infrastructure.persistence.entity.OutboxEventEntity;
import com.goride.payment.infrastructure.persistence.entity.PaymentAttemptEntity;
import com.goride.payment.infrastructure.persistence.entity.PaymentEntity;
import com.goride.payment.infrastructure.persistence.repository.OutboxEventRepository;
import com.goride.payment.infrastructure.persistence.repository.PaymentAttemptRepository;
import com.goride.payment.infrastructure.persistence.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production-ready implementation of PaymentService.
 * Enforces business invariants, persistent idempotency, and clean exception handling.
 */
@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              PaymentAttemptRepository paymentAttemptRepository) {
        this(paymentRepository, paymentAttemptRepository, null, null);
    }

    @Autowired
    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              PaymentAttemptRepository paymentAttemptRepository,
                              @Autowired(required = false) OutboxEventRepository outboxEventRepository,
                              @Autowired(required = false) ObjectMapper objectMapper) {
        this.paymentRepository = Objects.requireNonNull(paymentRepository, "paymentRepository must not be null");
        this.paymentAttemptRepository = Objects.requireNonNull(paymentAttemptRepository, "paymentAttemptRepository must not be null");
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentResponse processPayment(ProcessPaymentRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        log.info("Processing payment for tripId={}, passengerId={}, amount={} VND, method={}",
                request.getTripId(), request.getPassengerId(), request.getAmount(), request.getPaymentMethod());

        // 1. Idempotency Check: if payment for this tripId was already recorded, return existing record
        Optional<PaymentEntity> existingOpt = paymentRepository.findByTripId(request.getTripId());
        if (existingOpt.isPresent()) {
            PaymentEntity existing = existingOpt.get();
            log.info("Payment already exists for tripId={} with status={}. Returning existing record (Idempotent response).",
                    request.getTripId(), existing.getStatus());
            return PaymentResponse.from(existing);
        }

        // 2. Prepare new PaymentEntity
        Instant now = Instant.now();
        PaymentEntity payment = new PaymentEntity();
        payment.setTripId(request.getTripId());
        payment.setPassengerId(request.getPassengerId());
        payment.setAmount(request.getAmount());
        payment.setCurrency("VND");
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setProvider(request.getPaymentMethod() != null ? request.getPaymentMethod().trim().toUpperCase() : "SIMULATED");
        payment.setProviderReference("SIM-PAY-" + UUID.randomUUID());
        // Explicitly maintain invariant: completedAt >= createdAt
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        payment.setCompletedAt(now);

        // Save new PaymentEntity.
        PaymentEntity savedPayment = paymentRepository.save(payment);

        // 3. Record PaymentAttemptEntity for audit and retry history
        PaymentAttemptEntity attempt = new PaymentAttemptEntity();
        attempt.setPayment(savedPayment);
        attempt.setAttemptNo(1);
        attempt.setStatus(PaymentAttemptStatus.SUCCESS);
        attempt.setRequestedAt(now);
        attempt.setCompletedAt(now);
        paymentAttemptRepository.save(attempt);

        // 4. Record Outbox Event for transaction-outbox pattern
        saveOutboxEvent(savedPayment, UUID.randomUUID(), null, now);

        registerCommitLog(savedPayment);

        return PaymentResponse.from(savedPayment);
    }

    @Override
    public PaymentResponse processPaymentFromTripCompleted(UUID tripId, UUID passengerId, BigDecimal amount,
                                                           UUID correlationId, UUID causationId) {
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(passengerId, "passengerId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");

        log.info("Processing payment from trip.completed event: tripId={}, passengerId={}, amount={}",
                tripId, passengerId, amount);

        // 1. Idempotency Check: if payment for this tripId was already recorded, return existing record
        Optional<PaymentEntity> existingOpt = paymentRepository.findByTripId(tripId);
        if (existingOpt.isPresent()) {
            PaymentEntity existing = existingOpt.get();
            log.info("Payment already exists for tripId={} with status={}. Returning existing record.",
                    tripId, existing.getStatus());
            return PaymentResponse.from(existing);
        }

        // 2. Check for failure condition (e.g. non-positive amount)
        Instant now = Instant.now();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Payment failed for tripId={} due to non-positive amount: {}", tripId, amount);
            PaymentEntity failedPayment = new PaymentEntity();
            failedPayment.setTripId(tripId);
            failedPayment.setPassengerId(passengerId);
            failedPayment.setAmount(amount);
            failedPayment.setCurrency("VND");
            failedPayment.setStatus(PaymentStatus.FAILED);
            failedPayment.setProvider("SIMULATED");
            failedPayment.setProviderReference("SIM-PAY-FAILED-" + UUID.randomUUID());
            failedPayment.setCreatedAt(now);
            failedPayment.setUpdatedAt(now);
            PaymentEntity savedFailedPayment = paymentRepository.save(failedPayment);

            PaymentAttemptEntity attempt = new PaymentAttemptEntity();
            attempt.setPayment(savedFailedPayment);
            attempt.setAttemptNo(1);
            attempt.setStatus(PaymentAttemptStatus.FAILED);
            attempt.setFailureCode("INVALID_FARE_AMOUNT");
            attempt.setFailureMessage("Fare amount must be greater than zero");
            attempt.setRequestedAt(now);
            attempt.setCompletedAt(now);
            paymentAttemptRepository.save(attempt);

            saveFailedOutboxEvent(savedFailedPayment, "INVALID_FARE_AMOUNT", "Fare amount must be greater than zero",
                    correlationId != null ? correlationId : UUID.randomUUID(), causationId, now);

            return PaymentResponse.from(savedFailedPayment);
        }

        // 3. Prepare and save PaymentEntity for successful payment
        PaymentEntity payment = new PaymentEntity();
        payment.setTripId(tripId);
        payment.setPassengerId(passengerId);
        payment.setAmount(amount);
        payment.setCurrency("VND");
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setProvider("SIMULATED");
        payment.setProviderReference("SIM-PAY-" + UUID.randomUUID());
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        payment.setCompletedAt(now);

        PaymentEntity savedPayment = paymentRepository.save(payment);

        // 3. Record PaymentAttemptEntity
        PaymentAttemptEntity attempt = new PaymentAttemptEntity();
        attempt.setPayment(savedPayment);
        attempt.setAttemptNo(1);
        attempt.setStatus(PaymentAttemptStatus.SUCCESS);
        attempt.setRequestedAt(now);
        attempt.setCompletedAt(now);
        paymentAttemptRepository.save(attempt);

        // 4. Record Outbox Event
        saveOutboxEvent(savedPayment, correlationId != null ? correlationId : UUID.randomUUID(), causationId, now);

        registerCommitLog(savedPayment);

        return PaymentResponse.from(savedPayment);
    }

    private void saveOutboxEvent(PaymentEntity savedPayment, UUID correlationId, UUID causationId, Instant now) {
        if (outboxEventRepository == null) {
            return;
        }
        PaymentCompletedPayload eventPayload = new PaymentCompletedPayload(
                savedPayment.getId(),
                savedPayment.getTripId(),
                savedPayment.getPassengerId(),
                savedPayment.getAmount(),
                savedPayment.getCurrency(),
                savedPayment.getProvider(),
                savedPayment.getProviderReference(),
                savedPayment.getCompletedAt()
        );
        try {
            String payloadJson = objectMapper != null
                    ? objectMapper.writeValueAsString(eventPayload)
                    : "{}";
            OutboxEventEntity outboxEvent = new OutboxEventEntity(
                    UUID.randomUUID(),
                    "PAYMENT",
                    savedPayment.getId(),
                    "payment.succeeded",
                    payloadJson,
                    correlationId != null ? correlationId : UUID.randomUUID(),
                    causationId,
                    now
            );
            outboxEventRepository.save(outboxEvent);
            log.info("Created outbox event [id={}, type=payment.succeeded, paymentId={}]",
                    outboxEvent.getId(), savedPayment.getId());
        } catch (Exception e) {
            log.error("Failed to serialize and save outbox event for paymentId={}", savedPayment.getId(), e);
            throw new RuntimeException("Failed to persist outbox event", e);
        }
    }

    private void saveFailedOutboxEvent(PaymentEntity savedPayment, String failureCode, String failureMessage,
                                       UUID correlationId, UUID causationId, Instant now) {
        if (outboxEventRepository == null) {
            return;
        }
        com.goride.common.event.PaymentFailedPayload eventPayload = new com.goride.common.event.PaymentFailedPayload(
                savedPayment.getId(),
                savedPayment.getTripId(),
                savedPayment.getPassengerId(),
                savedPayment.getAmount(),
                savedPayment.getCurrency(),
                failureCode,
                failureMessage,
                now
        );
        try {
            String payloadJson = objectMapper != null
                    ? objectMapper.writeValueAsString(eventPayload)
                    : "{}";
            OutboxEventEntity outboxEvent = new OutboxEventEntity(
                    UUID.randomUUID(),
                    "PAYMENT",
                    savedPayment.getId(),
                    "payment.failed",
                    payloadJson,
                    correlationId != null ? correlationId : UUID.randomUUID(),
                    causationId,
                    now
            );
            outboxEventRepository.save(outboxEvent);
            log.info("Created outbox event [id={}, type=payment.failed, paymentId={}]",
                    outboxEvent.getId(), savedPayment.getId());
        } catch (Exception e) {
            log.error("Failed to serialize and save payment.failed outbox event for paymentId={}", savedPayment.getId(), e);
            throw new RuntimeException("Failed to persist outbox event", e);
        }
    }

    private void registerCommitLog(PaymentEntity savedPayment) {
        final UUID paymentId = savedPayment.getId();
        final UUID savedTripId = savedPayment.getTripId();
        final BigDecimal savedAmount = savedPayment.getAmount();
        final String providerRef = savedPayment.getProviderReference();
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("Payment completed successfully: paymentId={}, tripId={}, amount={} VND, providerRef={}",
                            paymentId, savedTripId, savedAmount, providerRef);
                }
            });
        } else {
            log.debug("Skipped commit-confirmed payment success log because active transaction synchronization is unavailable: paymentId={}, tripId={}",
                    paymentId, savedTripId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByTripId(UUID tripId) {
        Objects.requireNonNull(tripId, "tripId must not be null");
        log.debug("Fetching payment for tripId={}", tripId);

        PaymentEntity payment = paymentRepository.findByTripId(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for trip: " + tripId));

        return PaymentResponse.from(payment);
    }
}
