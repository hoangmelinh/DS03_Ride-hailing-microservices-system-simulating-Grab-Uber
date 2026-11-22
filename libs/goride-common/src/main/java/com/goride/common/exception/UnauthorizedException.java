package com.goride.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends BaseBusinessException {

    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }
}
