package com.goride.common.exception;

import org.springframework.http.HttpStatus;

public class EntityNotFoundException extends BaseBusinessException {

    public EntityNotFoundException(String message) {
        super("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    public EntityNotFoundException(String entityName, Object id) {
        super("NOT_FOUND", String.format("%s not found with id: %s", entityName, id), HttpStatus.NOT_FOUND);
    }
}
