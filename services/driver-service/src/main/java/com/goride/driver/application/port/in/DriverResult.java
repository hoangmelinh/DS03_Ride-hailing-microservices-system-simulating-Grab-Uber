package com.goride.driver.application.port.in;

import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.Vehicle;

public record DriverResult(
        Driver driver,
        Vehicle vehicle
) {
}
