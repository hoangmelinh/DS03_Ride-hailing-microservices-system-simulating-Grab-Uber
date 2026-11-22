package com.goride.payment.application.service;

import com.goride.payment.application.dto.request.ProcessPaymentRequest;
import com.goride.payment.application.dto.response.PaymentResponse;

import java.util.UUID;

/**
 * Application service interface for Payment operations.
 */
public interface PaymentService {

    /**
     * Process payment for a completed trip.
     * Guaranteed to be idempotent based on tripId.
     *
     * @param request the payment details
     * @return PaymentResponse containing payment details
     */
    PaymentResponse processPayment(ProcessPaymentRequest request);

    /**
     * Retrieve payment details for a specific trip.
     *
     * @param tripId the unique identifier of the trip
     * @return PaymentResponse containing payment details
     */
    PaymentResponse getPaymentByTripId(UUID tripId);

    /**
     * Process payment triggered by domain event 'trip.completed'.
     * Idempotent by tripId; records Transactional Outbox event.
     *
     * @param tripId trip identifier
     * @param passengerId passenger identifier
     * @param amount final fare amount
     * @param correlationId tracing correlation ID
     * @param causationId trigger event ID
     * @return PaymentResponse containing payment details
     */
    PaymentResponse processPaymentFromTripCompleted(UUID tripId, UUID passengerId, java.math.BigDecimal amount,
                                                    UUID correlationId, UUID causationId);
}
