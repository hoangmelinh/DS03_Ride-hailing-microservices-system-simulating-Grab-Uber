package com.goride.matching.adapter.out.persistence.mapper;

import com.goride.matching.adapter.out.persistence.entity.*;
import com.goride.matching.domain.model.*;

public final class MatchingPersistenceMapper {

    private MatchingPersistenceMapper() {
    }

    public static MatchingSaga toDomain(MatchingSagaJpaEntity e) {
        if (e == null) return null;
        return new MatchingSaga(
                e.getId(),
                e.getTripId(),
                e.getSelectedDriverId(),
                e.getStatus(),
                e.getCurrentStep(),
                e.getCorrelationId(),
                e.getAttemptCount() != null ? e.getAttemptCount() : 0,
                e.getLastErrorCode(),
                e.getLastErrorMessage(),
                e.getVersion(),
                e.getStartedAt(),
                e.getUpdatedAt(),
                e.getCompletedAt()
        );
    }

    public static MatchingSagaJpaEntity toJpa(MatchingSaga d) {
        if (d == null) return null;
        MatchingSagaJpaEntity e = new MatchingSagaJpaEntity();
        e.setId(d.getId());
        e.setTripId(d.getTripId());
        e.setSelectedDriverId(d.getSelectedDriverId());
        e.setStatus(d.getStatus());
        e.setCurrentStep(d.getCurrentStep());
        e.setCorrelationId(d.getCorrelationId());
        e.setAttemptCount(d.getAttemptCount());
        e.setLastErrorCode(d.getLastErrorCode());
        e.setLastErrorMessage(d.getLastErrorMessage());
        e.setVersion(d.getVersion());
        e.setStartedAt(d.getStartedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        e.setCompletedAt(d.getCompletedAt());
        return e;
    }

    public static MatchingAttempt toDomain(MatchingAttemptJpaEntity e) {
        if (e == null) return null;
        return new MatchingAttempt(
                e.getId(),
                e.getSagaId(),
                e.getDriverId(),
                e.getCandidateRank(),
                e.getDistanceMeters(),
                e.getAttemptNo(),
                e.getReserveResult(),
                e.getFailureCode(),
                e.getFailureMessage(),
                e.getCreatedAt()
        );
    }

    public static MatchingAttemptJpaEntity toJpa(MatchingAttempt d) {
        if (d == null) return null;
        MatchingAttemptJpaEntity e = new MatchingAttemptJpaEntity();
        e.setId(d.getId());
        e.setSagaId(d.getSagaId());
        e.setDriverId(d.getDriverId());
        e.setCandidateRank(d.getCandidateRank());
        e.setDistanceMeters(d.getDistanceMeters());
        e.setAttemptNo(d.getAttemptNo());
        e.setReserveResult(d.getReserveResult());
        e.setFailureCode(d.getFailureCode());
        e.setFailureMessage(d.getFailureMessage());
        e.setCreatedAt(d.getCreatedAt());
        return e;
    }

    public static OutboxEvent toDomain(OutboxEventJpaEntity e) {
        if (e == null) return null;
        return new OutboxEvent(
                e.getId(),
                e.getAggregateType(),
                e.getAggregateId(),
                e.getEventType(),
                e.getEventVersion(),
                e.getPayload(),
                e.getCorrelationId(),
                e.getCausationId(),
                e.getOccurredAt(),
                e.getStatus(),
                e.getRetryCount() != null ? e.getRetryCount() : 0,
                e.getNextRetryAt(),
                e.getPublishedAt(),
                e.getCreatedAt()
        );
    }

    public static OutboxEventJpaEntity toJpa(OutboxEvent d) {
        if (d == null) return null;
        OutboxEventJpaEntity e = new OutboxEventJpaEntity();
        e.setId(d.getId());
        e.setAggregateType(d.getAggregateType());
        e.setAggregateId(d.getAggregateId());
        e.setEventType(d.getEventType());
        e.setEventVersion(d.getEventVersion());
        e.setPayload(d.getPayload());
        e.setCorrelationId(d.getCorrelationId());
        e.setCausationId(d.getCausationId());
        e.setOccurredAt(d.getOccurredAt());
        e.setStatus(d.getStatus());
        e.setRetryCount(d.getRetryCount());
        e.setNextRetryAt(d.getNextRetryAt());
        e.setPublishedAt(d.getPublishedAt());
        e.setCreatedAt(d.getCreatedAt());
        return e;
    }

    public static InboxEvent toDomain(InboxEventJpaEntity e) {
        if (e == null) return null;
        return new InboxEvent(e.getEventId(), e.getEventType(), e.getConsumer(), e.getCorrelationId(), e.getProcessedAt());
    }

    public static InboxEventJpaEntity toJpa(InboxEvent d) {
        if (d == null) return null;
        InboxEventJpaEntity e = new InboxEventJpaEntity();
        e.setEventId(d.getEventId());
        e.setEventType(d.getEventType());
        e.setConsumer(d.getConsumer());
        e.setCorrelationId(d.getCorrelationId());
        e.setProcessedAt(d.getProcessedAt());
        return e;
    }

    public static DeadLetterMessage toDomain(DeadLetterMessageJpaEntity e) {
        if (e == null) return null;
        return new DeadLetterMessage(
                e.getId(), e.getQueueName(), e.getExchangeName(), e.getRoutingKey(),
                e.getPayload(), e.getCorrelationId(), e.getExceptionClass(), e.getExceptionMessage(),
                e.getRetryCount() != null ? e.getRetryCount() : 3, e.getStatus(),
                e.getFailedAt(), e.getReprocessedAt(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    public static DeadLetterMessageJpaEntity toJpa(DeadLetterMessage d) {
        if (d == null) return null;
        DeadLetterMessageJpaEntity e = new DeadLetterMessageJpaEntity();
        e.setId(d.getId());
        e.setQueueName(d.getQueueName());
        e.setExchangeName(d.getExchangeName());
        e.setRoutingKey(d.getRoutingKey());
        e.setPayload(d.getPayload());
        e.setCorrelationId(d.getCorrelationId());
        e.setExceptionClass(d.getExceptionClass());
        e.setExceptionMessage(d.getExceptionMessage());
        e.setRetryCount(d.getRetryCount());
        e.setStatus(d.getStatus());
        e.setFailedAt(d.getFailedAt());
        e.setReprocessedAt(d.getReprocessedAt());
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        return e;
    }
}
