package com.goride.matching.adapter.in.web;

import com.goride.common.dto.ApiResponse;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.matching.application.port.in.ProcessTripCreatedCommand;
import com.goride.matching.application.port.in.ProcessTripCreatedUseCase;
import com.goride.matching.domain.model.MatchingSaga;
import com.goride.matching.domain.repository.MatchingSagaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matching")
public class MatchingController {

    private final ProcessTripCreatedUseCase processTripCreatedUseCase;
    private final MatchingSagaRepository matchingSagaRepository;

    public MatchingController(ProcessTripCreatedUseCase processTripCreatedUseCase,
                              MatchingSagaRepository matchingSagaRepository) {
        this.processTripCreatedUseCase = processTripCreatedUseCase;
        this.matchingSagaRepository = matchingSagaRepository;
    }

    @PostMapping("/trigger")
    public ResponseEntity<ApiResponse<MatchingSagaResponse>> triggerMatching(
            @Valid @RequestBody TriggerMatchingRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader) {
        UUID correlationId = parseUuidOrRandom(correlationIdHeader);
        MatchingSaga saga = processTripCreatedUseCase.processTripCreated(new ProcessTripCreatedCommand(
                UUID.randomUUID(),
                request.tripId(),
                request.passengerId(),
                request.pickupLatitude(),
                request.pickupLongitude(),
                correlationId
        ));
        return ResponseEntity.ok(ApiResponse.success(toResponse(saga)));
    }

    @GetMapping("/sagas/trip/{tripId}")
    public ResponseEntity<ApiResponse<MatchingSagaResponse>> getSagaByTripId(@PathVariable("tripId") UUID tripId) {
        MatchingSaga saga = matchingSagaRepository.findByTripId(tripId)
                .orElseThrow(() -> new EntityNotFoundException("MatchingSaga for trip", tripId));
        return ResponseEntity.ok(ApiResponse.success(toResponse(saga)));
    }

    private MatchingSagaResponse toResponse(MatchingSaga saga) {
        return new MatchingSagaResponse(
                saga.getId(),
                saga.getTripId(),
                saga.getSelectedDriverId(),
                saga.getStatus().name(),
                saga.getCurrentStep(),
                saga.getAttemptCount(),
                saga.getCorrelationId(),
                saga.getLastErrorCode(),
                saga.getStartedAt(),
                saga.getCompletedAt()
        );
    }

    private UUID parseUuidOrRandom(String header) {
        if (header != null && !header.isBlank()) {
            try {
                return UUID.fromString(header.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return UUID.randomUUID();
    }

    public record TriggerMatchingRequest(
            @NotNull UUID tripId,
            UUID passengerId,
            @NotNull BigDecimal pickupLatitude,
            @NotNull BigDecimal pickupLongitude
    ) {}

    public record MatchingSagaResponse(
            UUID sagaId,
            UUID tripId,
            UUID selectedDriverId,
            String status,
            String currentStep,
            int attemptCount,
            UUID correlationId,
            String lastErrorCode,
            Instant startedAt,
            Instant completedAt
    ) {}
}
