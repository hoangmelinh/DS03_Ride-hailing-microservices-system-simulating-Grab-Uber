package com.goride.payment.application.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.payment.application.dto.request.ProcessPaymentRequest;
import com.goride.payment.application.dto.response.PaymentResponse;
import com.goride.payment.application.service.impl.PaymentServiceImpl;
import com.goride.payment.domain.model.PaymentAttemptStatus;
import com.goride.payment.domain.model.PaymentStatus;
import com.goride.payment.infrastructure.persistence.entity.OutboxEventEntity;
import com.goride.payment.infrastructure.persistence.entity.PaymentAttemptEntity;
import com.goride.payment.infrastructure.persistence.entity.PaymentEntity;
import com.goride.payment.infrastructure.persistence.repository.OutboxEventRepository;
import com.goride.payment.infrastructure.persistence.repository.PaymentAttemptRepository;
import com.goride.payment.infrastructure.persistence.repository.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private UUID tripId;
    private UUID passengerId;
    private BigDecimal amount;
    private ProcessPaymentRequest request;

    @BeforeEach
    void setUp() {
        tripId = UUID.randomUUID();
        passengerId = UUID.randomUUID();
        amount = new BigDecimal("50000.00");
        request = new ProcessPaymentRequest(tripId, passengerId, amount, "MOCK_WALLET");
    }

    @Test
    @DisplayName("Process payment successfully for a new trip")
    void processPayment_NewPayment_Success() {
        when(paymentRepository.findByTripId(tripId)).thenReturn(Optional.empty());

        PaymentEntity savedPayment = new PaymentEntity(tripId, passengerId, amount, "VND");
        savedPayment.setId(UUID.randomUUID());
        savedPayment.setStatus(PaymentStatus.SUCCEEDED);
        savedPayment.setProvider("MOCK_WALLET");
        savedPayment.setProviderReference("SIM-PAY-12345");
        savedPayment.setCompletedAt(Instant.now());

        when(paymentRepository.save(any(PaymentEntity.class))).thenReturn(savedPayment);

        PaymentResponse response = paymentService.processPayment(request);

        assertThat(response).isNotNull();
        assertThat(response.tripId()).isEqualTo(tripId);
        assertThat(response.passengerId()).isEqualTo(passengerId);
        assertThat(response.amount()).isEqualTo(amount);
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.provider()).isEqualTo("MOCK_WALLET");

        verify(paymentRepository).findByTripId(tripId);

        ArgumentCaptor<PaymentEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        PaymentEntity capturedPayment = paymentCaptor.getValue();
        assertThat(capturedPayment.getCreatedAt()).isNotNull();
        assertThat(capturedPayment.getCompletedAt()).isNotNull();
        assertThat(capturedPayment.getCompletedAt()).isAfterOrEqualTo(capturedPayment.getCreatedAt());

        ArgumentCaptor<PaymentAttemptEntity> attemptCaptor = ArgumentCaptor.forClass(PaymentAttemptEntity.class);
        verify(paymentAttemptRepository).save(attemptCaptor.capture());
        PaymentAttemptEntity savedAttempt = attemptCaptor.getValue();
        assertThat(savedAttempt.getAttemptNo()).isEqualTo(1);
        assertThat(savedAttempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
        assertThat(savedAttempt.getPayment()).isEqualTo(savedPayment);
        assertThat(savedAttempt.getRequestedAt()).isNotNull();
        assertThat(savedAttempt.getCompletedAt()).isNotNull();
        assertThat(savedAttempt.getCompletedAt()).isAfterOrEqualTo(savedAttempt.getRequestedAt());
    }

    @Test
    @DisplayName("Idempotency: Return existing payment when payment for tripId already exists")
    void processPayment_ExistingPayment_Idempotent() {
        PaymentEntity existingPayment = new PaymentEntity(tripId, passengerId, amount, "VND");
        existingPayment.setId(UUID.randomUUID());
        existingPayment.setStatus(PaymentStatus.SUCCEEDED);
        existingPayment.setProvider("MOCK_WALLET");
        existingPayment.setCompletedAt(Instant.now());

        when(paymentRepository.findByTripId(tripId)).thenReturn(Optional.of(existingPayment));

        PaymentResponse response = paymentService.processPayment(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(existingPayment.getId());
        assertThat(response.tripId()).isEqualTo(tripId);
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);

        verify(paymentRepository).findByTripId(tripId);
        verify(paymentRepository, never()).save(any(PaymentEntity.class));
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    @Test
    @DisplayName("Concurrency: DataIntegrityViolationException on save propagates out cleanly to rollback transaction")
    void processPayment_ConcurrentRaceCondition_PropagatesException() {
        when(paymentRepository.findByTripId(tripId)).thenReturn(Optional.empty());

        when(paymentRepository.save(any(PaymentEntity.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation: uq_payments_trip_id"));

        assertThatThrownBy(() -> paymentService.processPayment(request))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(paymentRepository).findByTripId(tripId);
        verify(paymentRepository).save(any(PaymentEntity.class));
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    @Test
    @DisplayName("Get payment by tripId returns payment details when found")
    void getPaymentByTripId_Found_Success() {
        PaymentEntity existingPayment = new PaymentEntity(tripId, passengerId, amount, "VND");
        existingPayment.setId(UUID.randomUUID());
        existingPayment.setStatus(PaymentStatus.SUCCEEDED);

        when(paymentRepository.findByTripId(tripId)).thenReturn(Optional.of(existingPayment));

        PaymentResponse response = paymentService.getPaymentByTripId(tripId);

        assertThat(response).isNotNull();
        assertThat(response.tripId()).isEqualTo(tripId);
        assertThat(response.amount()).isEqualTo(amount);
    }

    @Test
    @DisplayName("Get payment by tripId throws EntityNotFoundException when payment does not exist")
    void getPaymentByTripId_NotFound_ThrowsException() {
        when(paymentRepository.findByTripId(tripId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentByTripId(tripId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(tripId.toString());
    }

    @AfterEach
    void tearDownTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    @DisplayName("AUD-P1-005: Active transaction + active sync registers afterCommit and emits success log only on commit")
    void processPayment_ActiveTxAndSync_RegistersAfterCommitAndLogsOnCommit() {
        Logger logger = (Logger) LoggerFactory.getLogger(PaymentServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            stubNewPaymentSave();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();

            paymentService.processPayment(request);

            List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
            assertThat(syncs).hasSize(1);
            assertThat(appender.list)
                    .noneMatch(e -> e.getFormattedMessage().contains("Payment completed successfully"));

            syncs.forEach(TransactionSynchronization::afterCommit);

            assertThat(appender.list)
                    .anyMatch(e -> e.getFormattedMessage().contains("Payment completed successfully"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("AUD-P1-005: Transaction rollback does not emit payment success log")
    void processPayment_TransactionRollback_DoesNotEmitSuccessLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(PaymentServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            stubNewPaymentSave();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();

            paymentService.processPayment(request);

            List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
            assertThat(syncs).hasSize(1);
            syncs.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            assertThat(appender.list)
                    .noneMatch(e -> e.getFormattedMessage().contains("Payment completed successfully"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("AUD-P1-005: Active transaction + inactive sync avoids IllegalStateException and does not emit success log")
    void processPayment_ActiveTxWithoutSync_NoExceptionAndNoSuccessLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(PaymentServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            stubNewPaymentSave();
            TransactionSynchronizationManager.setActualTransactionActive(true);

            PaymentResponse response = paymentService.processPayment(request);

            assertThat(response).isNotNull();
            assertThat(appender.list)
                    .noneMatch(e -> e.getFormattedMessage().contains("Payment completed successfully"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("AUD-P1-005: Active sync + inactive transaction does not register callback or emit success log")
    void processPayment_ActiveSyncWithoutActualTx_NoRegistrationAndNoSuccessLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(PaymentServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            stubNewPaymentSave();
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.initSynchronization();

            PaymentResponse response = paymentService.processPayment(request);

            assertThat(response).isNotNull();
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
            assertThat(appender.list)
                    .noneMatch(e -> e.getFormattedMessage().contains("Payment completed successfully"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("AUD-P1-005: Neither transaction nor sync active keeps business behavior intact without emitting success log")
    void processPayment_NoActiveTxOrSync_DoesNotEmitSuccessLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(PaymentServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            stubNewPaymentSave();

            PaymentResponse response = paymentService.processPayment(request);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(appender.list)
                    .noneMatch(e -> e.getFormattedMessage().contains("Payment completed successfully"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    private void stubNewPaymentSave() {
        when(paymentRepository.findByTripId(tripId)).thenReturn(Optional.empty());
        PaymentEntity savedPayment = new PaymentEntity(tripId, passengerId, amount, "VND");
        savedPayment.setId(UUID.randomUUID());
        savedPayment.setStatus(PaymentStatus.SUCCEEDED);
        savedPayment.setProvider("MOCK_WALLET");
        savedPayment.setProviderReference("SIM-PAY-12345");
        Instant now = Instant.now();
        savedPayment.setCreatedAt(now);
        savedPayment.setCompletedAt(now);
        when(paymentRepository.save(any(PaymentEntity.class))).thenReturn(savedPayment);
    }

    @Test
    @DisplayName("processPaymentFromTripCompleted: Creates payment, attempt, and outbox event")
    void processPaymentFromTripCompleted_Success() {
        OutboxEventRepository mockOutboxRepo = mock(OutboxEventRepository.class);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        PaymentServiceImpl serviceWithOutbox = new PaymentServiceImpl(
                paymentRepository, paymentAttemptRepository, mockOutboxRepo, mapper
        );

        when(paymentRepository.findByTripId(tripId)).thenReturn(Optional.empty());

        PaymentEntity saved = new PaymentEntity(tripId, passengerId, amount, "VND");
        saved.setId(UUID.randomUUID());
        saved.setStatus(PaymentStatus.SUCCEEDED);
        saved.setProvider("SIMULATED");
        saved.setProviderReference("SIM-PAY-999");
        saved.setCreatedAt(Instant.now());
        saved.setCompletedAt(Instant.now());
        when(paymentRepository.save(any(PaymentEntity.class))).thenReturn(saved);

        UUID correlationId = UUID.randomUUID();
        UUID causationId = UUID.randomUUID();

        PaymentResponse response = serviceWithOutbox.processPaymentFromTripCompleted(
                tripId, passengerId, amount, correlationId, causationId
        );

        assertThat(response).isNotNull();
        assertThat(response.tripId()).isEqualTo(tripId);
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);

        verify(paymentRepository).save(any(PaymentEntity.class));
        verify(paymentAttemptRepository).save(any(PaymentAttemptEntity.class));

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(mockOutboxRepo).save(outboxCaptor.capture());
        OutboxEventEntity outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent.getEventType()).isEqualTo("payment.succeeded");
        assertThat(outboxEvent.getAggregateType()).isEqualTo("PAYMENT");
        assertThat(outboxEvent.getCorrelationId()).isEqualTo(correlationId);
        assertThat(outboxEvent.getCausationId()).isEqualTo(causationId);
        assertThat(outboxEvent.getPayload()).contains(tripId.toString());
    }

    @Test
    @DisplayName("processPaymentFromTripCompleted: Returns existing payment when already paid")
    void processPaymentFromTripCompleted_ExistingPayment_Idempotent() {
        OutboxEventRepository mockOutboxRepo = mock(OutboxEventRepository.class);
        ObjectMapper mapper = new ObjectMapper();
        PaymentServiceImpl serviceWithOutbox = new PaymentServiceImpl(
                paymentRepository, paymentAttemptRepository, mockOutboxRepo, mapper
        );

        PaymentEntity existing = new PaymentEntity(tripId, passengerId, amount, "VND");
        existing.setId(UUID.randomUUID());
        existing.setStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByTripId(tripId)).thenReturn(Optional.of(existing));

        PaymentResponse response = serviceWithOutbox.processPaymentFromTripCompleted(
                tripId, passengerId, amount, UUID.randomUUID(), UUID.randomUUID()
        );

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(existing.getId());
        verify(paymentRepository, never()).save(any(PaymentEntity.class));
        verify(mockOutboxRepo, never()).save(any(OutboxEventEntity.class));
    }
}
