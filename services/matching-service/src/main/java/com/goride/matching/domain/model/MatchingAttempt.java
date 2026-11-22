package com.goride.matching.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class MatchingAttempt {

    private final UUID id;
    private final UUID sagaId;
    private final UUID driverId;
    private final int candidateRank;
    private final Integer distanceMeters;
    private final int attemptNo;
    private final ReserveResult reserveResult;
    private final String failureCode;
    private final String failureMessage;
    private final Instant createdAt;

    public MatchingAttempt(UUID id, UUID sagaId, UUID driverId, int candidateRank, Integer distanceMeters,
                           int attemptNo, ReserveResult reserveResult, String failureCode, String failureMessage,
                           Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.sagaId = Objects.requireNonNull(sagaId, "sagaId must not be null");
        this.driverId = Objects.requireNonNull(driverId, "driverId must not be null");
        this.candidateRank = candidateRank;
        this.distanceMeters = distanceMeters;
        this.attemptNo = attemptNo;
        this.reserveResult = reserveResult;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static MatchingAttempt record(UUID sagaId, UUID driverId, int candidateRank, Integer distanceMeters,
                                         int attemptNo, ReserveResult reserveResult,
                                         String failureCode, String failureMessage) {
        String truncatedMsg = (failureMessage != null && failureMessage.length() > 500)
                ? failureMessage.substring(0, 500) : failureMessage;
        String truncatedCode = (failureCode != null && failureCode.length() > 100)
                ? failureCode.substring(0, 100) : failureCode;
        return new MatchingAttempt(
                UUID.randomUUID(),
                sagaId,
                driverId,
                candidateRank,
                distanceMeters,
                attemptNo,
                reserveResult,
                truncatedCode,
                truncatedMsg,
                Instant.now()
        );
    }

    public UUID getId() { return id; }
    public UUID getSagaId() { return sagaId; }
    public UUID getDriverId() { return driverId; }
    public int getCandidateRank() { return candidateRank; }
    public Integer getDistanceMeters() { return distanceMeters; }
    public int getAttemptNo() { return attemptNo; }
    public ReserveResult getReserveResult() { return reserveResult; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
