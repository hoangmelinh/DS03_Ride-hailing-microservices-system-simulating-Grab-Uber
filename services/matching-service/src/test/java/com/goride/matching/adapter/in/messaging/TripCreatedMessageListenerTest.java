package com.goride.matching.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.goride.common.event.EventEnvelope;
import com.goride.matching.application.port.in.ProcessTripCreatedCommand;
import com.goride.matching.application.port.in.ProcessTripCreatedUseCase;
import com.goride.matching.domain.model.InboxEvent;
import com.goride.matching.domain.repository.InboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripCreatedMessageListenerTest {

    @Mock
    private InboxEventRepository inboxEventRepository;

    @Mock
    private ProcessTripCreatedUseCase processTripCreatedUseCase;

    private TripCreatedMessageListener listener;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        listener = new TripCreatedMessageListener(objectMapper, inboxEventRepository, processTripCreatedUseCase);
    }

    @Test
    @DisplayName("Inbox Deduplication: Should skip processing when eventId already exists in inbox_event")
    void handleEventEnvelope_DuplicateEventId_ShouldSkip() {
        UUID eventId = UUID.randomUUID();
        TripCreatedEventPayload payload = new TripCreatedEventPayload(
                UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("10.76"), new BigDecimal("106.66"), "Pickup",
                new BigDecimal("10.77"), new BigDecimal("106.69"), "Dest",
                new BigDecimal("50000"), "VND", "REQUESTED", Instant.now()
        );
        EventEnvelope<TripCreatedEventPayload> envelope = EventEnvelope.of(
                eventId, "trip.created", payload.tripId().toString(), "trip-service", UUID.randomUUID(), null, payload);

        when(inboxEventRepository.existsByEventId(eventId)).thenReturn(true);

        listener.handleEventEnvelope(envelope);

        verify(processTripCreatedUseCase, never()).processTripCreated(any());
        verify(inboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should invoke orchestrator and persist InboxEvent when eventId is new")
    void handleEventEnvelope_NewEventId_ShouldProcessAndSaveInbox() {
        UUID eventId = UUID.randomUUID();
        TripCreatedEventPayload payload = new TripCreatedEventPayload(
                UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("10.76"), new BigDecimal("106.66"), "Pickup",
                new BigDecimal("10.77"), new BigDecimal("106.69"), "Dest",
                new BigDecimal("50000"), "VND", "REQUESTED", Instant.now()
        );
        EventEnvelope<TripCreatedEventPayload> envelope = EventEnvelope.of(
                eventId, "trip.created", payload.tripId().toString(), "trip-service", UUID.randomUUID(), null, payload);

        when(inboxEventRepository.existsByEventId(eventId)).thenReturn(false);

        listener.handleEventEnvelope(envelope);

        verify(processTripCreatedUseCase, times(1)).processTripCreated(any(ProcessTripCreatedCommand.class));
        verify(inboxEventRepository, times(1)).save(any(InboxEvent.class));
    }
}
