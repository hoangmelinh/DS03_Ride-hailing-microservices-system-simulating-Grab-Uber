package com.goride.payment.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.payment.application.dto.request.ProcessPaymentRequest;
import com.goride.payment.application.dto.response.PaymentResponse;
import com.goride.payment.application.service.PaymentService;
import com.goride.payment.domain.model.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PaymentController.class, PaymentGlobalExceptionHandler.class})
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @Test
    @DisplayName("POST /api/v1/payments/process with valid body returns 200 and standard ApiResponse")
    void processPayment_ValidRequest_Returns200() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("75000.00");

        ProcessPaymentRequest request = new ProcessPaymentRequest(tripId, passengerId, amount, "MOCK_WALLET");

        PaymentResponse mockResponse = new PaymentResponse(
                UUID.randomUUID(),
                tripId,
                passengerId,
                amount,
                "VND",
                PaymentStatus.SUCCEEDED,
                "MOCK_WALLET",
                "SIM-PAY-123",
                Instant.now(),
                Instant.now()
        );

        when(paymentService.processPayment(any(ProcessPaymentRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tripId").value(tripId.toString()))
                .andExpect(jsonPath("$.data.passengerId").value(passengerId.toString()))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.amount").value(75000.00))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/v1/payments/process with missing tripId returns 400 Bad Request")
    void processPayment_MissingTripId_Returns400() throws Exception {
        ProcessPaymentRequest invalidRequest = new ProcessPaymentRequest(
                null,
                UUID.randomUUID(),
                new BigDecimal("50000.00"),
                "MOCK_WALLET"
        );

        mockMvc.perform(post("/api/v1/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/payments/process with amount less than 1,000 VND returns 400 Bad Request")
    void processPayment_AmountTooSmall_Returns400() throws Exception {
        ProcessPaymentRequest invalidRequest = new ProcessPaymentRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("500.00"), // Below 1,000 VND
                "MOCK_WALLET"
        );

        mockMvc.perform(post("/api/v1/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{tripId} returns 200 when payment exists")
    void getPaymentByTripId_Found_Returns200() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("120000.00");

        PaymentResponse mockResponse = new PaymentResponse(
                UUID.randomUUID(),
                tripId,
                passengerId,
                amount,
                "VND",
                PaymentStatus.SUCCEEDED,
                "SIMULATED_CARD",
                "SIM-PAY-999",
                Instant.now(),
                Instant.now()
        );

        when(paymentService.getPaymentByTripId(tripId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/payments/{tripId}", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tripId").value(tripId.toString()))
                .andExpect(jsonPath("$.data.amount").value(120000.00));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{tripId} returns 404 when payment not found")
    void getPaymentByTripId_NotFound_Returns404() throws Exception {
        UUID tripId = UUID.randomUUID();

        when(paymentService.getPaymentByTripId(tripId))
                .thenThrow(new EntityNotFoundException("Payment not found for trip: " + tripId));

        mockMvc.perform(get("/api/v1/payments/{tripId}", tripId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Payment not found for trip: " + tripId));
    }
}
