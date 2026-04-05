package com.an.llm.connector.gateway.exception;

public class ApiFallbackException extends RuntimeException{
    public ApiFallbackException(String message){
        super(message);
    }
}
