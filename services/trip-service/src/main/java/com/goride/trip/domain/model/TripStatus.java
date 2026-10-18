package com.goride.trip.domain.model;

public enum TripStatus {
    REQUESTED,
    MATCHING,
    DRIVER_ASSIGNED,
    ACCEPTED,
    IN_PROGRESS,
    COMPLETED,
    PAYMENT_PENDING,
    PAID,
    PAYMENT_FAILED,
    CANCELLED
}
