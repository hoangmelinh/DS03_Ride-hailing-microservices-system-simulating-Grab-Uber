package com.goride.payment.domain.model;

public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
