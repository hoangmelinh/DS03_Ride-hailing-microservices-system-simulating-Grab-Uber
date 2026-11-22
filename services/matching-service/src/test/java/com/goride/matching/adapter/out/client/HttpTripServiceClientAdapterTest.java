package com.goride.matching.adapter.out.client;

import com.goride.matching.application.port.out.TripServiceClientPort.AssignCallStatus;
import com.goride.matching.application.port.out.TripServiceClientPort.AssignTripOutcome;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpTripServiceClientAdapterTest {

    private HttpServer server;
    private String baseUrl;
    private AtomicReference<String> capturedMethod;
    private AtomicReference<String> capturedPath;

    @BeforeEach
    void setUp() throws IOException {
        capturedMethod = new AtomicReference<>();
        capturedPath = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            capturedMethod.set(exchange.getRequestMethod());
            capturedPath.set(exchange.getRequestURI().getPath());
            byte[] response = "{}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("JdkClientHttpRequestFactory should execute HTTP PATCH /api/v1/trips/{id}/assign without Invalid HTTP method error")
    void assignDriver_ShouldSupportHttpPatch() {
        HttpTripServiceClientAdapter adapter = new HttpTripServiceClientAdapter(
                RestClient.builder(), baseUrl, 2000, 3000);
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        AssignTripOutcome outcome = adapter.assignDriver(tripId, driverId, "assign-key-1", correlationId);

        assertThat(outcome.status()).isEqualTo(AssignCallStatus.SUCCESS);
        assertThat(capturedMethod.get()).isEqualTo("PATCH");
        assertThat(capturedPath.get()).isEqualTo("/api/v1/trips/" + tripId + "/assign");
    }
}
