package com.goride.common;

import com.goride.common.dto.ApiResponse;
import com.goride.common.event.EventEnvelope;
import com.goride.common.event.PaymentCompletedPayload;
import com.goride.common.event.PaymentFailedPayload;
import com.goride.common.exception.DuplicateResourceException;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.common.handler.BaseGlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void testExceptionHandler_MethodArgumentTypeMismatch() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("tripId");
        doReturn(UUID.class).when(ex).getRequiredType();

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleMethodArgumentTypeMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("INVALID_PARAMETER", response.getBody().getError().getCode());
        assertTrue(response.getBody().getError().getMessage().contains("tripId"));
        assertTrue(response.getBody().getError().getMessage().contains("UUID"));
    }

    @Test
    void testPaymentCompletedPayload() {
        UUID paymentId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("75000.00");
        Instant now = Instant.now();

        PaymentCompletedPayload payload = new PaymentCompletedPayload(
                paymentId, tripId, passengerId, amount, "VND", "SIMULATED", "SIM-123", now
        );

        assertEquals(paymentId, payload.getPaymentId());
        assertEquals(tripId, payload.getTripId());
        assertEquals(passengerId, payload.getPassengerId());
        assertEquals(amount, payload.getAmount());
        assertEquals("VND", payload.getCurrency());
        assertEquals("SIMULATED", payload.getProvider());
        assertEquals("SIM-123", payload.getProviderReference());
        assertEquals(now, payload.getCompletedAt());
    }

    @Test
    void testPaymentFailedPayload() {
        UUID paymentId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("75000.00");
        Instant now = Instant.now();

        PaymentFailedPayload payload = new PaymentFailedPayload(
                paymentId, tripId, passengerId, amount, "VND", "CARD_DECLINED", "Card has expired", now
        );

        assertEquals(paymentId, payload.getPaymentId());
        assertEquals(tripId, payload.getTripId());
        assertEquals(passengerId, payload.getPassengerId());
        assertEquals(amount, payload.getAmount());
        assertEquals("VND", payload.getCurrency());
        assertEquals("CARD_DECLINED", payload.getFailureCode());
        assertEquals("Card has expired", payload.getFailureMessage());
        assertEquals(now, payload.getFailedAt());
    }
}
