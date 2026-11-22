package com.goride.driver.application.port.in;

import java.util.UUID;

public record UpdateDriverStatusCommand(
        UUID driverId,
        String status
) {
}
