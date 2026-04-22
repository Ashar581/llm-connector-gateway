package com.an.llm.connector.gateway.exception;

public class NotActivatedException extends RuntimeException {
    public NotActivatedException(String message) {
        super(message);
    }
}
