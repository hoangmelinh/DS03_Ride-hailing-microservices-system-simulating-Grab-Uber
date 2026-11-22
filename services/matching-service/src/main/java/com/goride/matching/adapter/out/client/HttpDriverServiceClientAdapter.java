package com.goride.matching.adapter.out.client;

import com.goride.common.dto.ApiResponse;
import com.goride.matching.application.port.out.DriverServiceClientPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class HttpDriverServiceClientAdapter implements DriverServiceClientPort {

    private static final Logger log = LoggerFactory.getLogger(HttpDriverServiceClientAdapter.class);

    private final RestClient restClient;

    public HttpDriverServiceClientAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${goride.clients.driver-service.base-url:http://localhost:8082}") String baseUrl,
            @Value("${goride.clients.driver-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${goride.clients.driver-service.read-timeout-ms:3000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public List<AvailableDriverCandidate> getAvailableDrivers(int limit) {
        try {
            ApiResponse<List<AvailableDriverDto>> response = restClient.get()
                    .uri("/api/v1/drivers/available?limit={limit}", limit)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || !response.isSuccess() || response.getData() == null) {
                return List.of();
            }

            return response.getData().stream()
                    .map(d -> new AvailableDriverCandidate(
                            d.driverId(), d.status(), d.latitude(), d.longitude(), d.locationUpdatedAt()))
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to fetch available drivers from driver-service: {}", ex.getMessage());
            return List.of();
        }
    }

    @Override
    public ReserveOutcome reserveDriver(UUID driverId, UUID tripId, String idempotencyKey,
                                        UUID correlationId, int ttlSeconds) {
        try {
            ApiResponse<ReserveResponseDto> response = restClient.post()
                    .uri("/api/v1/drivers/{id}/reserve", driverId)
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-Correlation-Id", correlationId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ReserveRequestDto(tripId, ttlSeconds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            UUID reservationId = (response != null && response.getData() != null)
                    ? response.getData().reservationId()
                    : null;
            return new ReserveOutcome(ReserveCallStatus.SUCCESS, reservationId, null, null);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                return new ReserveOutcome(ReserveCallStatus.CONFLICT_BUSY, null, "DRIVER_NOT_AVAILABLE", ex.getMessage());
            }
            return new ReserveOutcome(ReserveCallStatus.CLIENT_ERROR, null, "CLIENT_ERROR_" + ex.getStatusCode().value(), ex.getMessage());
        } catch (HttpServerErrorException ex) {
            return new ReserveOutcome(ReserveCallStatus.SERVER_ERROR, null, "SERVER_ERROR_" + ex.getStatusCode().value(), ex.getMessage());
        } catch (ResourceAccessException ex) {
            return new ReserveOutcome(ReserveCallStatus.TIMEOUT, null, "TIMEOUT", ex.getMessage());
        }
    }

    @Override
    public ReleaseOutcome releaseDriver(UUID driverId, UUID tripId, String idempotencyKey,
                                        UUID correlationId, String reason) {
        try {
            restClient.post()
                    .uri("/api/v1/drivers/{id}/release", driverId)
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-Correlation-Id", correlationId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ReleaseRequestDto(tripId, reason))
                    .retrieve()
                    .toBodilessEntity();
            return new ReleaseOutcome(ReleaseCallStatus.SUCCESS, null, null);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                return new ReleaseOutcome(ReleaseCallStatus.CONFLICT, "RELEASE_CONFLICT", ex.getMessage());
            }
            return new ReleaseOutcome(ReleaseCallStatus.CLIENT_ERROR, "CLIENT_ERROR_" + ex.getStatusCode().value(), ex.getMessage());
        } catch (HttpServerErrorException ex) {
            return new ReleaseOutcome(ReleaseCallStatus.SERVER_ERROR, "SERVER_ERROR_" + ex.getStatusCode().value(), ex.getMessage());
        } catch (ResourceAccessException ex) {
            return new ReleaseOutcome(ReleaseCallStatus.TIMEOUT, "TIMEOUT", ex.getMessage());
        }
    }

    private record AvailableDriverDto(UUID driverId, String status, BigDecimal latitude, BigDecimal longitude, Instant locationUpdatedAt) {}
    private record ReserveRequestDto(UUID tripId, Integer ttlSeconds) {}
    private record ReserveResponseDto(UUID reservationId, UUID driverId, UUID tripId, String status, Instant expiresAt) {}
    private record ReleaseRequestDto(UUID tripId, String reason) {}
}
