package com.goride.common;

import com.goride.common.dto.ApiResponse;
import com.goride.common.event.EventEnvelope;
import com.goride.common.exception.DuplicateResourceException;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.common.handler.BaseGlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CommonModuleTest {

    private final BaseGlobalExceptionHandler exceptionHandler = new BaseGlobalExceptionHandler();

    @Test
    void testApiResponseSuccess() {
        ApiResponse<String> response = ApiResponse.success("hello-goride");
        assertTrue(response.isSuccess());
        assertEquals("hello-goride", response.getData());
        assertNull(response.getError());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void testApiResponseError() {
        ApiResponse<Void> response = ApiResponse.error("NOT_FOUND", "Item not found");
        assertFalse(response.isSuccess());
        assertNull(response.getData());
        assertNotNull(response.getError());
        assertEquals("NOT_FOUND", response.getError().getCode());
        assertEquals("Item not found", response.getError().getMessage());
    }

    @Test
    void testEventEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        EventEnvelope<String> envelope = EventEnvelope.of(
                eventId,
                "user.registered",
                "user-123",
                "user-service",
                correlationId,
                null,
                "payload-test"
        );

        assertEquals(eventId, envelope.getEventId());
        assertEquals("user.registered", envelope.getEventType());
        assertEquals(1, envelope.getEventVersion());
        assertEquals("user-123", envelope.getAggregateId());
        assertEquals("user-service", envelope.getProducer());
        assertEquals(correlationId, envelope.getCorrelationId());
        assertEquals("payload-test", envelope.getPayload());
    }

    @Test
    void testExceptionHandler_EntityNotFound() {
        EntityNotFoundException ex = new EntityNotFoundException("User", "uuid-123");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleBusinessException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("NOT_FOUND", response.getBody().getError().getCode());
    }

    @Test
    void testExceptionHandler_DuplicateResource() {
        DuplicateResourceException ex = new DuplicateResourceException("email", "test@goride.com");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleBusinessException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("DUPLICATE_RESOURCE", response.getBody().getError().getCode());
    }
}
