package com.goride.matching.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.matching.application.port.out.CandidateDriverDiscoveryPort;
import com.goride.matching.application.port.out.DriverServiceClientPort;
import com.goride.matching.application.port.out.DriverServiceClientPort.ReleaseCallStatus;
import com.goride.matching.application.port.out.DriverServiceClientPort.ReleaseOutcome;
import com.goride.matching.application.port.out.TripServiceClientPort;
import com.goride.matching.application.port.out.TripServiceClientPort.AssignCallStatus;
import com.goride.matching.application.port.out.TripServiceClientPort.AssignTripOutcome;
import com.goride.matching.domain.model.MatchingSaga;
import com.goride.matching.domain.model.OutboxEvent;
import com.goride.matching.domain.model.SagaStatus;
import com.goride.matching.domain.repository.MatchingAttemptRepository;
import com.goride.matching.domain.repository.MatchingSagaRepository;
import com.goride.matching.domain.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaRecoveryServiceTest {

    @Mock
    private MatchingSagaRepository sagaRepository;

    @Mock
    private MatchingAttemptRepository attemptRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private CandidateDriverDiscoveryPort candidateDiscoveryPort;

    @Mock
    private DriverServiceClientPort driverServiceClientPort;

    @Mock
    private TripServiceClientPort tripServiceClientPort;

    private SagaRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        MatchingSagaOrchestrator orchestrator = new MatchingSagaOrchestrator(
                sagaRepository, attemptRepository, outboxEventRepository,
                candidateDiscoveryPort, driverServiceClientPort, tripServiceClientPort,
                new ObjectMapper(), 30, 2, 2);
        recoveryService = new SagaRecoveryService(sagaRepository, orchestrator, outboxEventRepository, 5, 3);
        lenient().when(sagaRepository.save(any(MatchingSaga.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Recovery Worker should retry COMPENSATING sagas and transition to COMPENSATED when release succeeds")
    void recoverPendingCompensations_ShouldReleaseAndMarkCompensated() {
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        MatchingSaga saga = MatchingSaga.start(tripId, correlationId);
        saga.markDriverReserved(driverId);
        saga.markCompensating("ASSIGN_FAILED", "error");

        when(sagaRepository.findByStatus(SagaStatus.COMPENSATING)).thenReturn(List.of(saga));
        when(sagaRepository.findByStatusAndCurrentStep(SagaStatus.DRIVER_RESERVED, "ASSIGN_TRIP_RECONCILE_PENDING"))
                .thenReturn(List.of());
        when(driverServiceClientPort.releaseDriver(eq(driverId), eq(tripId), anyString(), eq(correlationId), eq("ASSIGN_FAILED")))
                .thenReturn(new ReleaseOutcome(ReleaseCallStatus.SUCCESS, null, null));

        recoveryService.recoverStuckSagas();

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Recovery Worker should reconcile ASSIGN_TRIP_RECONCILE_PENDING and mark COMPLETED on success")
    void reconcilePendingAssignments_ShouldCompleteOnSuccess() {
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        MatchingSaga saga = MatchingSaga.start(tripId, correlationId);
        saga.markDriverReserved(driverId);
        saga.markAssignReconcilePending("ASSIGN_TIMEOUT", "timeout");

        when(sagaRepository.findByStatus(SagaStatus.COMPENSATING)).thenReturn(List.of());
        when(sagaRepository.findByStatusAndCurrentStep(SagaStatus.DRIVER_RESERVED, "ASSIGN_TRIP_RECONCILE_PENDING"))
                .thenReturn(List.of(saga));
        when(tripServiceClientPort.assignDriver(eq(tripId), eq(driverId), anyString(), eq(correlationId)))
                .thenReturn(new AssignTripOutcome(AssignCallStatus.SUCCESS, null, null));

        recoveryService.recoverStuckSagas();

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Recovery Worker should trigger compensation when ASSIGN_TRIP_RECONCILE_PENDING exceeds max reconcile attempts")
    void reconcilePendingAssignments_WhenMaxAttemptsExceeded_ShouldCompensate() {
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        MatchingSaga saga = MatchingSaga.start(tripId, correlationId);
        saga.markDriverReserved(driverId);
        saga.markAssignReconcilePending("ASSIGN_TIMEOUT", "timeout");

        when(sagaRepository.findByStatus(SagaStatus.COMPENSATING)).thenReturn(List.of());
        when(sagaRepository.findByStatusAndCurrentStep(SagaStatus.DRIVER_RESERVED, "ASSIGN_TRIP_RECONCILE_PENDING"))
                .thenReturn(List.of(saga));
        when(tripServiceClientPort.assignDriver(eq(tripId), eq(driverId), anyString(), eq(correlationId)))
                .thenReturn(new AssignTripOutcome(AssignCallStatus.TIMEOUT, "TRIP_ASSIGN_TIMEOUT", "timeout"));
        when(driverServiceClientPort.releaseDriver(eq(driverId), eq(tripId), anyString(), eq(correlationId), eq("ASSIGN_FAILED")))
                .thenReturn(new ReleaseOutcome(ReleaseCallStatus.SUCCESS, null, null));

        // 3 reconcile attempts still timing out
        recoveryService.recoverStuckSagas();
        recoveryService.recoverStuckSagas();
        recoveryService.recoverStuckSagas();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.DRIVER_RESERVED);
        assertThat(saga.getAttemptCount()).isEqualTo(3);

        // 4th tick exceeds maxReconcileAttempts=3 -> triggers compensation
        recoveryService.recoverStuckSagas();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        verify(driverServiceClientPort, times(1))
                .releaseDriver(eq(driverId), eq(tripId), anyString(), eq(correlationId), eq("ASSIGN_FAILED"));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }
}
