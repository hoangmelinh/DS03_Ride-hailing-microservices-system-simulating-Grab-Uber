package com.goride.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends BaseBusinessException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }
}
