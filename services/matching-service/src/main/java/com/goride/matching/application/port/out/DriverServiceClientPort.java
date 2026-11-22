package com.goride.matching.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DriverServiceClientPort {

    List<AvailableDriverCandidate> getAvailableDrivers(int limit);

    ReserveOutcome reserveDriver(UUID driverId, UUID tripId, String idempotencyKey, UUID correlationId, int ttlSeconds);

    ReleaseOutcome releaseDriver(UUID driverId, UUID tripId, String idempotencyKey, UUID correlationId, String reason);

    record AvailableDriverCandidate(
            UUID driverId,
            String status,
            BigDecimal latitude,
            BigDecimal longitude,
            Instant locationUpdatedAt
    ) {
    }

    enum ReserveCallStatus {
        SUCCESS,
        CONFLICT_BUSY,
        CLIENT_ERROR,
        SERVER_ERROR,
        TIMEOUT
    }

    record ReserveOutcome(
            ReserveCallStatus status,
            UUID reservationId,
            String errorCode,
            String errorMessage
    ) {
        public boolean isSuccess() {
            return status == ReserveCallStatus.SUCCESS;
        }
    }

    enum ReleaseCallStatus {
        SUCCESS,
        CONFLICT,
        CLIENT_ERROR,
        SERVER_ERROR,
        TIMEOUT
    }

    record ReleaseOutcome(
            ReleaseCallStatus status,
            String errorCode,
            String errorMessage
    ) {
        public boolean isSuccess() {
            return status == ReleaseCallStatus.SUCCESS;
        }
    }
}
