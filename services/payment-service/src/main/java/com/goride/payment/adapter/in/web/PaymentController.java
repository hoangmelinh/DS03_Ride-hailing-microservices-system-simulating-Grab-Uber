package com.goride.payment.adapter.in.web;

import com.goride.common.dto.ApiResponse;
import com.goride.payment.application.dto.request.ProcessPaymentRequest;
import com.goride.payment.application.dto.response.PaymentResponse;
import com.goride.payment.application.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * REST Controller exposing Payment endpoints.
 * Conforms strictly to Clean Architecture and standard ApiResponse envelope.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = Objects.requireNonNull(paymentService, "paymentService must not be null");
    }

    /**
     * Process payment for a trip (Mock / Simulated).
     *
     * @param request the process payment payload
     * @return ApiResponse containing the processed payment details
     */
    @PostMapping("/process")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @Valid @RequestBody ProcessPaymentRequest request) {
        log.info("Received REST request to process payment for tripId: {}", request.getTripId());
        PaymentResponse response = paymentService.processPayment(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Retrieve payment details by tripId.
     *
     * @param tripId the trip identifier
     * @return ApiResponse containing the payment details
     */
    @GetMapping("/{tripId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByTripId(
            @PathVariable UUID tripId) {
        log.info("Received REST request to get payment for tripId: {}", tripId);
        PaymentResponse response = paymentService.getPaymentByTripId(tripId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
