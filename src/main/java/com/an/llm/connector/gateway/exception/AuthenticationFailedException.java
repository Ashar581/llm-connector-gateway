package com.an.llm.connector.gateway.exception;

public class AuthenticationFailedException extends RuntimeException{
    public AuthenticationFailedException(String message) {
        super(message);
    }
}
