package com.goride.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayGlobalExceptionHandlerTest {

    private GatewayGlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GatewayGlobalExceptionHandler(new ObjectMapper());
    }

    @Test
    @DisplayName("NotFoundException returns 404 Not Found")
    void handle_NotFoundException_Returns404() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/unknown").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        handler.handle(exchange, new NotFoundException("Route not found")).block();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("ConnectException returns 503 Service Unavailable")
    void handle_ConnectException_Returns503() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/payments").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        handler.handle(exchange, new ConnectException("Connection refused")).block();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Generic Exception returns 500 Internal Server Error")
    void handle_GenericException_Returns500() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/trips").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        handler.handle(exchange, new RuntimeException("Unexpected error")).block();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
    }
}

