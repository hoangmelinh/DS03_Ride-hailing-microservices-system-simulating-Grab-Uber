package com.goride.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
    }

    @Test
    @DisplayName("Request without correlationId generates a new UUID and sets in request and response")
    void filter_NoCorrelationId_GeneratesAndPropagates() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/trips").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> forwardedCorrelationId = new AtomicReference<>();
        GatewayFilterChain chain = mutatedExchange -> {
            forwardedCorrelationId.set(mutatedExchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertNotNull(forwardedCorrelationId.get());
        assertDoesNotThrow(() -> UUID.fromString(forwardedCorrelationId.get()));

        String responseCorrelationId = exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertEquals(forwardedCorrelationId.get(), responseCorrelationId);
    }

    @Test
    @DisplayName("Request with existing correlationId preserves it across request and response")
    void filter_ExistingCorrelationId_PreservesValue() {
        String existingId = UUID.randomUUID().toString();
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/trips")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> forwardedCorrelationId = new AtomicReference<>();
        GatewayFilterChain chain = mutatedExchange -> {
            forwardedCorrelationId.set(mutatedExchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertEquals(existingId, forwardedCorrelationId.get());
        assertEquals(existingId, exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER));
    }
}

