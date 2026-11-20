package com.goride.payment.application.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
    @DecimalMax(value = "9999999999.99", message = "amount must not exceed 9,999,999,999.99 VND")
    @Digits(integer = 10, fraction = 2, message = "amount must have at most 10 integer digits and 2 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "paymentMethod is required")
    @Size(max = 30, message = "paymentMethod must not exceed 30 characters")
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
