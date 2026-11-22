package com.goride.driver.application.port.in;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateDriverLocationCommand(
        UUID driverId,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
