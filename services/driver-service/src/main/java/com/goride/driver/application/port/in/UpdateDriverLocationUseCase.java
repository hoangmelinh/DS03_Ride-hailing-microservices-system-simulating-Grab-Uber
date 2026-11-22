package com.goride.driver.application.port.in;

import com.goride.driver.domain.model.DriverLocation;

public interface UpdateDriverLocationUseCase {

    DriverLocation updateLocation(UpdateDriverLocationCommand command);
}
