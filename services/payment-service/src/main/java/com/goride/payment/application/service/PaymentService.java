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
     * Process payment triggered asynchronously from a RabbitMQ trip.completed event.
     * Enforces Inbox deduplication and generates an Outbox payment.succeeded event.
     */
    PaymentResponse processPaymentFromEvent(UUID eventId, UUID tripId, UUID passengerId,
                                            java.math.BigDecimal amount, String currency, UUID correlationId);

    /**
     * Retrieve payment details for a specific trip.
     *
     * @param tripId the unique identifier of the trip
     * @return PaymentResponse containing payment details
     */
    PaymentResponse getPaymentByTripId(UUID tripId);
}
