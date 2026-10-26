package com.goride.payment.application.service;

import com.goride.common.exception.EntityNotFoundException;
import com.goride.payment.application.dto.request.ProcessPaymentRequest;
import com.goride.payment.application.dto.response.PaymentResponse;
import com.goride.payment.application.service.impl.PaymentServiceImpl;
import com.goride.payment.domain.model.PaymentAttemptStatus;
import com.goride.payment.domain.model.PaymentStatus;
import com.goride.payment.infrastructure.persistence.entity.PaymentAttemptEntity;
import com.goride.payment.infrastructure.persistence.entity.PaymentEntity;
import com.goride.payment.infrastructure.persistence.repository.PaymentAttemptRepository;
import com.goride.payment.infrastructure.persistence.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        verify(paymentRepository).save(any(PaymentEntity.class));

        ArgumentCaptor<PaymentAttemptEntity> attemptCaptor = ArgumentCaptor.forClass(PaymentAttemptEntity.class);
        verify(paymentAttemptRepository).save(attemptCaptor.capture());
        PaymentAttemptEntity savedAttempt = attemptCaptor.getValue();
        assertThat(savedAttempt.getAttemptNo()).isEqualTo(1);
        assertThat(savedAttempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
        assertThat(savedAttempt.getPayment()).isEqualTo(savedPayment);
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
    @DisplayName("Concurrency: Recover gracefully when DataIntegrityViolationException occurs on save")
    void processPayment_ConcurrentRaceCondition_RecoversExisting() {
        when(paymentRepository.findByTripId(tripId)).thenReturn(Optional.empty());

        PaymentEntity concurrentPayment = new PaymentEntity(tripId, passengerId, amount, "VND");
        concurrentPayment.setId(UUID.randomUUID());
        concurrentPayment.setStatus(PaymentStatus.SUCCEEDED);

        when(paymentRepository.save(any(PaymentEntity.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation: uq_payments_trip_id"));

        when(paymentRepository.findByTripId(tripId))
                .thenReturn(Optional.empty()) // First call in step 1
                .thenReturn(Optional.of(concurrentPayment)); // Second call in catch block

        PaymentResponse response = paymentService.processPayment(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(concurrentPayment.getId());
        assertThat(response.tripId()).isEqualTo(tripId);
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
}
