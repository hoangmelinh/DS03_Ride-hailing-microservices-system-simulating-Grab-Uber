package com.goride.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;

/**
 * Global reactive error handler for Spring Cloud Gateway.
 * Intercepts routing failures, timeouts, connection errors and 404s,
 * returning standard ApiResponse envelope.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayGlobalExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayGlobalExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GatewayGlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status;
        String errorCode;
        String errorMessage;

        if (ex instanceof NotFoundException) {
            status = HttpStatus.NOT_FOUND;
            errorCode = "NOT_FOUND";
            errorMessage = "Service or route not found";
            log.warn("Route not found: {}", ex.getMessage());
        } else if (ex instanceof TimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            errorCode = "GATEWAY_TIMEOUT";
            errorMessage = "Target microservice request timed out";
            log.error("Gateway request timeout: {}", ex.getMessage());
        } else if (ex instanceof ConnectException || (ex.getCause() != null && ex.getCause() instanceof ConnectException)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            errorCode = "SERVICE_UNAVAILABLE";
            errorMessage = "Target microservice is currently unreachable";
            log.error("Downstream connection failed: {}", ex.getMessage());
        } else if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            errorCode = status.name();
            errorMessage = rse.getReason() != null ? rse.getReason() : rse.getMessage();
            log.warn("Response status exception: {} - {}", status, errorMessage);
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            errorCode = "INTERNAL_SERVER_ERROR";
            errorMessage = "An unexpected error occurred in API Gateway";
            log.error("Unhandled gateway exception on {}: {}", exchange.getRequest().getPath(), ex.getMessage(), ex);
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> apiResponse = ApiResponse.error(errorCode, errorMessage);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(apiResponse);
        } catch (JsonProcessingException jsonEx) {
            bytes = ("{\"success\":false,\"error\":{\"code\":\"" + errorCode + "\",\"message\":\"" + errorMessage + "\"}}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}

