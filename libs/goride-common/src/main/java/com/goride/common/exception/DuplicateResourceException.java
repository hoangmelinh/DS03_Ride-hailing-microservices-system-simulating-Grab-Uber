package com.goride.common.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends BaseBusinessException {

    public DuplicateResourceException(String message) {
        super("DUPLICATE_RESOURCE", message, HttpStatus.CONFLICT);
    }

    public DuplicateResourceException(String field, Object value) {
        super("DUPLICATE_RESOURCE", String.format("Resource with %s '%s' already exists", field, value), HttpStatus.CONFLICT);
    }
}
