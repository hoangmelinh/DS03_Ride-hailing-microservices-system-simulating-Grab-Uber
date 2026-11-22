package com.goride.matching.adapter.out.client;

import com.goride.matching.application.port.out.TripServiceClientPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
public class HttpTripServiceClientAdapter implements TripServiceClientPort {

    private final RestClient restClient;

    public HttpTripServiceClientAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${goride.clients.trip-service.base-url:http://localhost:8083}") String baseUrl,
            @Value("${goride.clients.trip-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${goride.clients.trip-service.read-timeout-ms:3000}") int readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public AssignTripOutcome assignDriver(UUID tripId, UUID driverId, String idempotencyKey, UUID correlationId) {
        try {
            restClient.patch()
                    .uri("/api/v1/trips/{id}/assign", tripId)
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-Correlation-Id", correlationId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AssignDriverRequestDto(driverId))
                    .retrieve()
                    .toBodilessEntity();
            return new AssignTripOutcome(AssignCallStatus.SUCCESS, null, null);
        } catch (HttpClientErrorException ex) {
            return new AssignTripOutcome(
                    AssignCallStatus.BUSINESS_REJECTED,
                    "TRIP_ASSIGN_REJECTED_" + ex.getStatusCode().value(),
                    ex.getMessage());
        } catch (HttpServerErrorException ex) {
            return new AssignTripOutcome(
                    AssignCallStatus.SERVER_ERROR,
                    "TRIP_SERVER_ERROR_" + ex.getStatusCode().value(),
                    ex.getMessage());
        } catch (ResourceAccessException ex) {
            return new AssignTripOutcome(
                    AssignCallStatus.TIMEOUT,
                    "TRIP_ASSIGN_TIMEOUT",
                    ex.getMessage());
        }
    }

    private record AssignDriverRequestDto(UUID driverId) {}
}
