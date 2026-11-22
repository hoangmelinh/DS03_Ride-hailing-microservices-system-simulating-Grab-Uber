package com.goride.driver.application.port.in;

import com.goride.driver.domain.model.Driver;

public interface UpdateDriverStatusUseCase {

    Driver updateDriverStatus(UpdateDriverStatusCommand command);
}
