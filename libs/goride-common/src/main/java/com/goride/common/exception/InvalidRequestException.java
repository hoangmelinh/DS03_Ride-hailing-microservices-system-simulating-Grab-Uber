package com.goride.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends BaseBusinessException {

    public InvalidRequestException(String message) {
        super("INVALID_REQUEST", message, HttpStatus.BAD_REQUEST);
    }
}
