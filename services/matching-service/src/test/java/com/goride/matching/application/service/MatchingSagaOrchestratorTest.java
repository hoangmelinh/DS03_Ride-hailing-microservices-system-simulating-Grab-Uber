package com.goride.matching.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.matching.application.port.in.ProcessTripCreatedCommand;
import com.goride.matching.application.port.out.CandidateDriverDiscoveryPort;
import com.goride.matching.application.port.out.CandidateDriverDiscoveryPort.RankedDriverCandidate;
import com.goride.matching.application.port.out.DriverServiceClientPort;
import com.goride.matching.application.port.out.DriverServiceClientPort.*;
import com.goride.matching.application.port.out.TripServiceClientPort;
import com.goride.matching.application.port.out.TripServiceClientPort.AssignCallStatus;
import com.goride.matching.application.port.out.TripServiceClientPort.AssignTripOutcome;
import com.goride.matching.domain.model.*;
import com.goride.matching.domain.repository.MatchingAttemptRepository;
import com.goride.matching.domain.repository.MatchingSagaRepository;
import com.goride.matching.domain.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingSagaOrchestratorTest {

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

    private MatchingSagaOrchestrator orchestrator;

    private UUID tripId;
    private UUID passengerId;
    private UUID correlationId;
    private UUID eventId;
    private ProcessTripCreatedCommand command;

    @BeforeEach
    void setUp() {
        orchestrator = new MatchingSagaOrchestrator(
                sagaRepository,
                attemptRepository,
                outboxEventRepository,
                candidateDiscoveryPort,
                driverServiceClientPort,
                tripServiceClientPort,
                new ObjectMapper(),
                30,
                2,
                2
        );
        tripId = UUID.randomUUID();
        passengerId = UUID.randomUUID();
        correlationId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        command = new ProcessTripCreatedCommand(
                eventId, tripId, passengerId,
                new BigDecimal("10.762622"), new BigDecimal("106.660172"), correlationId
        );

        lenient().when(sagaRepository.save(any(MatchingSaga.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(attemptRepository.save(any(MatchingAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("1. Happy Path: Candidate -> Reserve 200 -> Assign Trip 200 -> Saga COMPLETED + Outbox matching.completed")
    void happyPath_ShouldCompleteSagaAndSaveOutbox() {
        UUID driverId = UUID.randomUUID();
        when(sagaRepository.findByTripId(tripId)).thenReturn(Optional.empty());
        when(candidateDiscoveryPort.findCandidates(any(), any()))
                .thenReturn(List.of(new RankedDriverCandidate(driverId, 1, 250)));
        when(driverServiceClientPort.reserveDriver(eq(driverId), eq(tripId), anyString(), eq(correlationId), eq(30)))
                .thenReturn(new ReserveOutcome(ReserveCallStatus.SUCCESS, UUID.randomUUID(), null, null));
        when(tripServiceClientPort.assignDriver(eq(tripId), eq(driverId), anyString(), eq(correlationId)))
                .thenReturn(new AssignTripOutcome(AssignCallStatus.SUCCESS, null, null));

        MatchingSaga result = orchestrator.processTripCreated(command);

        assertThat(result.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(result.getSelectedDriverId()).isEqualTo(driverId);
        assertThat(result.getCompletedAt()).isNotNull();

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("matching.completed");
        assertThat(outboxCaptor.getValue().getPayload()).contains(driverId.toString());
    }

    @Test
    @DisplayName("2. Candidate Fallback: Candidate #1 returns 409 CONFLICT_BUSY -> Candidate #2 reserves 200 -> COMPLETED")
    void candidateFallbackOn409_ShouldTryNextCandidateAndComplete() {
        UUID driver1 = UUID.randomUUID();
        UUID driver2 = UUID.randomUUID();
        when(sagaRepository.findByTripId(tripId)).thenReturn(Optional.empty());
        when(candidateDiscoveryPort.findCandidates(any(), any()))
                .thenReturn(List.of(
                        new RankedDriverCandidate(driver1, 1, 100),
                        new RankedDriverCandidate(driver2, 2, 300)
                ));
        when(driverServiceClientPort.reserveDriver(eq(driver1), eq(tripId), anyString(), eq(correlationId), eq(30)))
                .thenReturn(new ReserveOutcome(ReserveCallStatus.CONFLICT_BUSY, null, "DRIVER_NOT_AVAILABLE", "409"));
        when(driverServiceClientPort.reserveDriver(eq(driver2), eq(tripId), anyString(), eq(correlationId), eq(30)))
                .thenReturn(new ReserveOutcome(ReserveCallStatus.SUCCESS, UUID.randomUUID(), null, null));
        when(tripServiceClientPort.assignDriver(eq(tripId), eq(driver2), anyString(), eq(correlationId)))
                .thenReturn(new AssignTripOutcome(AssignCallStatus.SUCCESS, null, null));

        MatchingSaga result = orchestrator.processTripCreated(command);

        assertThat(result.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(result.getSelectedDriverId()).isEqualTo(driver2);
        assertThat(result.getAttemptCount()).isEqualTo(2);

        ArgumentCaptor<MatchingAttempt> attemptCaptor = ArgumentCaptor.forClass(MatchingAttempt.class);
        verify(attemptRepository, times(2)).save(attemptCaptor.capture());
        assertThat(attemptCaptor.getAllValues().get(0).getReserveResult()).isEqualTo(ReserveResult.DRIVER_BUSY);
        assertThat(attemptCaptor.getAllValues().get(1).getReserveResult()).isEqualTo(ReserveResult.SUCCESS);
    }

    @Test
    @DisplayName("3. Saga Compensation: Assign Trip fails -> Release Driver 200 -> Saga COMPENSATED + Outbox matching.failed")
    void sagaCompensation_WhenAssignFails_ShouldReleaseDriverAndMarkCompensated() {
        UUID driverId = UUID.randomUUID();
        when(sagaRepository.findByTripId(tripId)).thenReturn(Optional.empty());
        when(candidateDiscoveryPort.findCandidates(any(), any()))
                .thenReturn(List.of(new RankedDriverCandidate(driverId, 1, 200)));
        when(driverServiceClientPort.reserveDriver(eq(driverId), eq(tripId), anyString(), eq(correlationId), eq(30)))
                .thenReturn(new ReserveOutcome(ReserveCallStatus.SUCCESS, UUID.randomUUID(), null, null));
        when(tripServiceClientPort.assignDriver(eq(tripId), eq(driverId), anyString(), eq(correlationId)))
                .thenReturn(new AssignTripOutcome(AssignCallStatus.BUSINESS_REJECTED, "TRIP_CANCELLED", "409"));
        when(driverServiceClientPort.releaseDriver(eq(driverId), eq(tripId), anyString(), eq(correlationId), eq("ASSIGN_FAILED")))
                .thenReturn(new ReleaseOutcome(ReleaseCallStatus.SUCCESS, null, null));

        MatchingSaga result = orchestrator.processTripCreated(command);

        assertThat(result.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        verify(driverServiceClientPort, times(1))
                .releaseDriver(eq(driverId), eq(tripId), anyString(), eq(correlationId), eq("ASSIGN_FAILED"));

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("matching.failed");
    }

    @Test
    @DisplayName("4. Reserve Timeout: Must retry with EXACT SAME Idempotency-Key before moving to next candidate")
    void reserveTimeout_ShouldRetryWithSameIdempotencyKey() {
        UUID driverId = UUID.randomUUID();
        when(sagaRepository.findByTripId(tripId)).thenReturn(Optional.empty());
        when(candidateDiscoveryPort.findCandidates(any(), any()))
                .thenReturn(List.of(new RankedDriverCandidate(driverId, 1, 150)));
        when(driverServiceClientPort.reserveDriver(eq(driverId), eq(tripId), anyString(), eq(correlationId), eq(30)))
                .thenReturn(new ReserveOutcome(ReserveCallStatus.TIMEOUT, null, "TIMEOUT", "Read timed out"))
                .thenReturn(new ReserveOutcome(ReserveCallStatus.SUCCESS, UUID.randomUUID(), null, null));
        when(tripServiceClientPort.assignDriver(eq(tripId), eq(driverId), anyString(), eq(correlationId)))
                .thenReturn(new AssignTripOutcome(AssignCallStatus.SUCCESS, null, null));

        MatchingSaga result = orchestrator.processTripCreated(command);

        assertThat(result.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        ArgumentCaptor<String> idempCaptor = ArgumentCaptor.forClass(String.class);
        verify(driverServiceClientPort, times(2))
                .reserveDriver(eq(driverId), eq(tripId), idempCaptor.capture(), eq(correlationId), eq(30));
        assertThat(idempCaptor.getAllValues().get(0)).isEqualTo(idempCaptor.getAllValues().get(1));
    }

    @Test
    @DisplayName("5. Assign Timeout: Must NOT release driver blindly; keeps DRIVER_RESERVED for reconciliation")
    void assignTimeout_ShouldNotReleaseBlindlyAndDeferToReconciliation() {
        UUID driverId = UUID.randomUUID();
        when(sagaRepository.findByTripId(tripId)).thenReturn(Optional.empty());
        when(candidateDiscoveryPort.findCandidates(any(), any()))
                .thenReturn(List.of(new RankedDriverCandidate(driverId, 1, 150)));
        when(driverServiceClientPort.reserveDriver(eq(driverId), eq(tripId), anyString(), eq(correlationId), eq(30)))
                .thenReturn(new ReserveOutcome(ReserveCallStatus.SUCCESS, UUID.randomUUID(), null, null));
        when(tripServiceClientPort.assignDriver(eq(tripId), eq(driverId), anyString(), eq(correlationId)))
                .thenReturn(new AssignTripOutcome(AssignCallStatus.TIMEOUT, "TRIP_ASSIGN_TIMEOUT", "timeout"));

        MatchingSaga result = orchestrator.processTripCreated(command);

        assertThat(result.getStatus()).isEqualTo(SagaStatus.DRIVER_RESERVED);
        assertThat(result.getCurrentStep()).isEqualTo("ASSIGN_TRIP_RECONCILE_PENDING");
        verify(driverServiceClientPort, never()).releaseDriver(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("6. Compensation Failure: Keeps COMPENSATING status for Recovery Worker")
    void compensationFailure_ShouldKeepCompensatingStatus() {
        UUID driverId = UUID.randomUUID();
        when(sagaRepository.findByTripId(tripId)).thenReturn(Optional.empty());
        when(candidateDiscoveryPort.findCandidates(any(), any()))
                .thenReturn(List.of(new RankedDriverCandidate(driverId, 1, 150)));
        when(driverServiceClientPort.reserveDriver(eq(driverId), eq(tripId), anyString(), eq(correlationId), eq(30)))
                .thenReturn(new ReserveOutcome(ReserveCallStatus.SUCCESS, UUID.randomUUID(), null, null));
        when(tripServiceClientPort.assignDriver(eq(tripId), eq(driverId), anyString(), eq(correlationId)))
                .thenReturn(new AssignTripOutcome(AssignCallStatus.SERVER_ERROR, "TRIP_SERVER_ERROR_500", "500"));
        when(driverServiceClientPort.releaseDriver(eq(driverId), eq(tripId), anyString(), eq(correlationId), anyString()))
                .thenReturn(new ReleaseOutcome(ReleaseCallStatus.SERVER_ERROR, "SERVER_ERROR_500", "500"));

        MatchingSaga result = orchestrator.processTripCreated(command);

        assertThat(result.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("7. No Candidates: Marks Saga FAILED with NO_CANDIDATE_FOUND")
    void noCandidates_ShouldMarkSagaFailed() {
        when(sagaRepository.findByTripId(tripId)).thenReturn(Optional.empty());
        when(candidateDiscoveryPort.findCandidates(any(), any())).thenReturn(List.of());

        MatchingSaga result = orchestrator.processTripCreated(command);

        assertThat(result.getStatus()).isEqualTo(SagaStatus.FAILED);
        assertThat(result.getLastErrorCode()).isEqualTo("NO_CANDIDATE_FOUND");
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }
}
