package com.goride.payment.application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.payment.application.dto.request.ProcessPaymentRequest;
import com.goride.payment.application.dto.response.PaymentResponse;
import com.goride.payment.application.service.PaymentService;
import com.goride.payment.domain.model.OutboxStatus;
import com.goride.payment.domain.model.PaymentAttemptStatus;
import com.goride.payment.domain.model.PaymentStatus;
import com.goride.payment.infrastructure.persistence.entity.InboxEventEntity;
import com.goride.payment.infrastructure.persistence.entity.OutboxEventEntity;
import com.goride.payment.infrastructure.persistence.entity.PaymentAttemptEntity;
import com.goride.payment.infrastructure.persistence.entity.PaymentEntity;
import com.goride.payment.infrastructure.persistence.repository.InboxEventRepository;
import com.goride.payment.infrastructure.persistence.repository.OutboxEventRepository;
import com.goride.payment.infrastructure.persistence.repository.PaymentAttemptRepository;
import com.goride.payment.infrastructure.persistence.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Enforces business invariants, persistent idempotency, transactional outbox and clean exception handling.
 */
@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final InboxEventRepository inboxEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              PaymentAttemptRepository paymentAttemptRepository,
                              InboxEventRepository inboxEventRepository,
                              OutboxEventRepository outboxEventRepository,
                              ObjectMapper objectMapper) {
        this.paymentRepository = Objects.requireNonNull(paymentRepository, "paymentRepository must not be null");
        this.paymentAttemptRepository = Objects.requireNonNull(paymentAttemptRepository, "paymentAttemptRepository must not be null");
        this.inboxEventRepository = Objects.requireNonNull(inboxEventRepository, "inboxEventRepository must not be null");
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository, "outboxEventRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
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

        // Save new PaymentEntity. If a concurrent race condition occurs on unique constraint uq_payments_trip_id,
        // DataIntegrityViolationException propagates out cleanly to trigger a rollback of this transaction,
        // avoiding query execution on an aborted PostgreSQL transaction block.
        PaymentEntity savedPayment = paymentRepository.save(payment);

        // 3. Record PaymentAttemptEntity for audit and retry history
        PaymentAttemptEntity attempt = new PaymentAttemptEntity();
        attempt.setPayment(savedPayment);
        attempt.setAttemptNo(1);
        attempt.setStatus(PaymentAttemptStatus.SUCCESS);
        attempt.setRequestedAt(now);
        attempt.setCompletedAt(now);
        paymentAttemptRepository.save(attempt);

        // Register commit-confirmed success log only when both an actual transaction and synchronization are active
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

        // 4. Save Transactional Outbox Event
        saveOutboxPaymentSucceeded(savedPayment, UUID.randomUUID(), null);

        return PaymentResponse.from(savedPayment);
    }

    @Override
    public PaymentResponse processPaymentFromEvent(UUID eventId, UUID tripId, UUID passengerId,
                                                   BigDecimal amount, String currency, UUID correlationId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(passengerId, "passengerId must not be null");

        log.info("Processing asynchronous payment from eventId={} for tripId={}, passengerId={}, amount={} {}",
                eventId, tripId, passengerId, amount, currency);

        // 1. Inbox Deduplication: if event was already processed by payment-service, skip idempotently
        if (inboxEventRepository.existsByEventIdAndConsumer(eventId, "payment-service")) {
            log.info("Duplicate eventId={} for consumer 'payment-service'. Skipping.", eventId);
            return paymentRepository.findByTripId(tripId)
                    .map(PaymentResponse::from)
                    .orElse(null);
        }

        // 2. Check if payment already exists for this tripId
        Optional<PaymentEntity> existingOpt = paymentRepository.findByTripId(tripId);
        PaymentEntity payment;
        if (existingOpt.isPresent()) {
            payment = existingOpt.get();
            log.info("Payment already recorded for tripId={}. Returning existing record.", tripId);
        } else {
            Instant now = Instant.now();
            payment = new PaymentEntity();
            payment.setTripId(tripId);
            payment.setPassengerId(passengerId);
            payment.setAmount(amount != null ? amount : BigDecimal.ZERO);
            payment.setCurrency(currency != null ? currency : "VND");
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setProvider("SIMULATED");
            payment.setProviderReference("SIM-PAY-" + UUID.randomUUID());
            payment.setCreatedAt(now);
            payment.setUpdatedAt(now);
            payment.setCompletedAt(now);

            payment = paymentRepository.save(payment);

            PaymentAttemptEntity attempt = new PaymentAttemptEntity();
            attempt.setPayment(payment);
            attempt.setAttemptNo(1);
            attempt.setStatus(PaymentAttemptStatus.SUCCESS);
            attempt.setRequestedAt(now);
            attempt.setCompletedAt(now);
            paymentAttemptRepository.save(attempt);

            // Save Outbox Event for payment.succeeded
            saveOutboxPaymentSucceeded(payment, correlationId, eventId);
        }

        // 3. Record Inbox Event to mark this eventId as processed
        InboxEventEntity inboxEvent = new InboxEventEntity(
                eventId,
                "trip.completed",
                "payment-service",
                correlationId
        );
        inboxEventRepository.save(inboxEvent);

        log.info("Successfully processed payment from trip.completed eventId={}, paymentId={}, tripId={}",
                eventId, payment.getId(), tripId);

        return PaymentResponse.from(payment);
    }

    private void saveOutboxPaymentSucceeded(PaymentEntity payment, UUID correlationId, UUID causationId) {
        try {
            PaymentResponse response = PaymentResponse.from(payment);
            String payloadJson = objectMapper.writeValueAsString(response);

            OutboxEventEntity outboxEvent = new OutboxEventEntity(
                    UUID.randomUUID(),
                    "PAYMENT",
                    payment.getId(),
                    "payment.succeeded",
                    payloadJson,
                    correlationId != null ? correlationId : UUID.randomUUID(),
                    causationId,
                    Instant.now()
            );
            outboxEventRepository.save(outboxEvent);
            log.debug("Enqueued payment.succeeded outbox event for paymentId={}", payment.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payment.succeeded outbox payload for paymentId={}", payment.getId(), e);
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
