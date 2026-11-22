package com.goride.trip.application.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Request payload for cancelling a trip.
 */
public class CancelTripRequest {

    @Size(max = 255, message = "Cancellation reason must not exceed 255 characters")
    private String reason;

    public CancelTripRequest() {
    }

    public CancelTripRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
