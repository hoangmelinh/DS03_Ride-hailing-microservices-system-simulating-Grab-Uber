package com.goride.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload carried inside EventEnvelope for event 'payment.failed'.
 * Emitted by Payment Service via Transactional Outbox upon failed fare charge attempt.
 */
public class PaymentFailedPayload {

    private UUID paymentId;
    private UUID tripId;
    private UUID passengerId;
    private BigDecimal amount;
    private String currency;
    private String failureCode;
    private String failureMessage;
    private Instant failedAt;

    public PaymentFailedPayload() {
    }

    public PaymentFailedPayload(UUID paymentId, UUID tripId, UUID passengerId, BigDecimal amount,
                               String currency, String failureCode, String failureMessage, Instant failedAt) {
        this.paymentId = paymentId;
        this.tripId = tripId;
        this.passengerId = passengerId;
        this.amount = amount;
        this.currency = currency != null ? currency : "VND";
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.failedAt = failedAt != null ? failedAt : Instant.now();
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public UUID getPassengerId() {
        return passengerId;
    }

    public void setPassengerId(UUID passengerId) {
        this.passengerId = passengerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }
}
