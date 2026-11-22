package com.goride.matching.application.port.out;

import java.util.UUID;

public interface TripServiceClientPort {

    AssignTripOutcome assignDriver(UUID tripId, UUID driverId, String idempotencyKey, UUID correlationId);

    enum AssignCallStatus {
        SUCCESS,
        BUSINESS_REJECTED,
        SERVER_ERROR,
        TIMEOUT
    }

    record AssignTripOutcome(
            AssignCallStatus status,
            String errorCode,
            String errorMessage
    ) {
        public boolean isSuccess() {
            return status == AssignCallStatus.SUCCESS;
        }
    }
}
