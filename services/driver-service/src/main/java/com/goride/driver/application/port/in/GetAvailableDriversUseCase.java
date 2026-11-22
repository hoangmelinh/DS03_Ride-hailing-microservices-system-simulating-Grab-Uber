package com.goride.driver.application.port.in;

import java.util.List;

public interface GetAvailableDriversUseCase {

    List<AvailableDriverResult> getAvailableDrivers(Integer limit);
}
