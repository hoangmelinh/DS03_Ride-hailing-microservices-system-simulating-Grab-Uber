package com.goride.driver.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateDriverStatusRequest(
        @NotBlank(message = "Status is required")
        String status
) {
}
