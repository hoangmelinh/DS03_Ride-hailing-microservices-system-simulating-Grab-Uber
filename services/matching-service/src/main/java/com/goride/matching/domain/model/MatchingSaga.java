package com.goride.matching.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class MatchingSaga {

    private final UUID id;
    private final UUID tripId;
    private UUID selectedDriverId;
    private SagaStatus status;
    private String currentStep;
    private final UUID correlationId;
    private int attemptCount;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Long version;
    private final Instant startedAt;
    private Instant updatedAt;
    private Instant completedAt;

    public MatchingSaga(UUID id, UUID tripId, UUID selectedDriverId, SagaStatus status, String currentStep,
                        UUID correlationId, int attemptCount, String lastErrorCode, String lastErrorMessage,
                        Long version, Instant startedAt, Instant updatedAt, Instant completedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tripId = Objects.requireNonNull(tripId, "tripId must not be null");
        this.selectedDriverId = selectedDriverId;
        this.status = status != null ? status : SagaStatus.STARTED;
        this.currentStep = currentStep;
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
        this.attemptCount = attemptCount;
        this.lastErrorCode = lastErrorCode;
        this.lastErrorMessage = lastErrorMessage;
        this.version = version != null ? version : 0L;
        this.startedAt = startedAt != null ? startedAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        this.completedAt = completedAt;
    }

    public static MatchingSaga start(UUID tripId, UUID correlationId) {
        Instant now = Instant.now();
        return new MatchingSaga(
                UUID.randomUUID(),
                tripId,
                null,
                SagaStatus.STARTED,
                "DISCOVER_CANDIDATES",
                correlationId,
                0,
                null,
                null,
                0L,
                now,
                now,
                null
        );
    }

    public void markCandidateSelected() {
        this.status = SagaStatus.CANDIDATE_SELECTED;
        this.currentStep = "RESERVE_DRIVER";
        this.updatedAt = Instant.now();
    }

    public void incrementAttempt() {
        this.attemptCount += 1;
        this.updatedAt = Instant.now();
    }

    public void markDriverReserved(UUID driverId) {
        this.selectedDriverId = Objects.requireNonNull(driverId, "driverId must not be null");
        this.status = SagaStatus.DRIVER_RESERVED;
        this.currentStep = "ASSIGN_TRIP";
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void markAssignReconcilePending(String errorCode, String errorMessage) {
        this.currentStep = "ASSIGN_TRIP_RECONCILE_PENDING";
        this.attemptCount = 0;
        this.lastErrorCode = truncate(errorCode, 100);
        this.lastErrorMessage = truncate(errorMessage, 500);
        this.updatedAt = Instant.now();
    }

    public void markTripAssigned() {
        this.status = SagaStatus.TRIP_ASSIGNED;
        this.currentStep = "COMPLETE_SAGA";
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void markCompleted() {
        Instant now = Instant.now();
        this.status = SagaStatus.COMPLETED;
        this.currentStep = "COMPLETED";
        this.updatedAt = now;
        this.completedAt = now;
    }

    public void markCompensating(String errorCode, String errorMessage) {
        this.status = SagaStatus.COMPENSATING;
        this.currentStep = "RELEASE_DRIVER";
        this.attemptCount = 0;
        this.lastErrorCode = truncate(errorCode, 100);
        this.lastErrorMessage = truncate(errorMessage, 500);
        this.updatedAt = Instant.now();
    }

    public void markCompensated() {
        Instant now = Instant.now();
        this.status = SagaStatus.COMPENSATED;
        this.currentStep = "COMPENSATED";
        this.updatedAt = now;
        this.completedAt = now;
    }

    public void markFailed(String errorCode, String errorMessage) {
        Instant now = Instant.now();
        this.status = SagaStatus.FAILED;
        this.currentStep = "FAILED";
        this.lastErrorCode = truncate(errorCode, 100);
        this.lastErrorMessage = truncate(errorMessage, 500);
        this.updatedAt = now;
        this.completedAt = now;
    }

    public void recordTransientError(String errorCode, String errorMessage) {
        this.lastErrorCode = truncate(errorCode, 100);
        this.lastErrorMessage = truncate(errorMessage, 500);
        this.updatedAt = Instant.now();
    }

    private static String truncate(String val, int maxLen) {
        if (val == null || val.length() <= maxLen) return val;
        return val.substring(0, maxLen);
    }

    public UUID getId() { return id; }
    public UUID getTripId() { return tripId; }
    public UUID getSelectedDriverId() { return selectedDriverId; }
    public SagaStatus getStatus() { return status; }
    public String getCurrentStep() { return currentStep; }
    public UUID getCorrelationId() { return correlationId; }
    public int getAttemptCount() { return attemptCount; }
    public String getLastErrorCode() { return lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public Long getVersion() { return version; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
