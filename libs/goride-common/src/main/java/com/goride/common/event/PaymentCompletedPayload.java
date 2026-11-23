package com.goride.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload carried inside EventEnvelope for event 'payment.succeeded'.
 * Emitted by Payment Service via Transactional Outbox upon successful fare charge.
 * Consumed by Notification Service, Trip Service, and other interested subscribers.
 */
public class PaymentCompletedPayload {

    private UUID paymentId;
    private UUID tripId;
    private UUID passengerId;
    private BigDecimal amount;
    private String currency;
    private String provider;
    private String providerReference;
    private Instant completedAt;

    public PaymentCompletedPayload() {
    }

    public PaymentCompletedPayload(UUID paymentId, UUID tripId, UUID passengerId, BigDecimal amount,
                                  String currency, String provider, String providerReference, Instant completedAt) {
        this.paymentId = paymentId;
        this.tripId = tripId;
        this.passengerId = passengerId;
        this.amount = amount;
        this.currency = currency != null ? currency : "VND";
        this.provider = provider;
        this.providerReference = providerReference;
        this.completedAt = completedAt != null ? completedAt : Instant.now();
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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
