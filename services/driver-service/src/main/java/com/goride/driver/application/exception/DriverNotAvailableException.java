package com.goride.driver.application.exception;

import com.goride.common.exception.BaseBusinessException;
import org.springframework.http.HttpStatus;

public class DriverNotAvailableException extends BaseBusinessException {

    public DriverNotAvailableException(String message) {
        super("DRIVER_NOT_AVAILABLE", message, HttpStatus.CONFLICT);
    }
}

