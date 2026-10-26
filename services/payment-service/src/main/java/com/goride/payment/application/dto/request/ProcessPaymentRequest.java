package com.goride.payment.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request payload for initiating payment processing.
 */
public class ProcessPaymentRequest {

    @NotNull(message = "tripId is required")
    private UUID tripId;

    @NotNull(message = "passengerId is required")
    private UUID passengerId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "1000.00", message = "amount must be at least 1,000 VND")
    private BigDecimal amount;

    @NotBlank(message = "paymentMethod is required")
    private String paymentMethod; // e.g. "MOCK_WALLET", "CASH", "SIMULATED_CARD"

    public ProcessPaymentRequest() {
    }

    public ProcessPaymentRequest(UUID tripId, UUID passengerId, BigDecimal amount, String paymentMethod) {
        this.tripId = tripId;
        this.passengerId = passengerId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
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

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
