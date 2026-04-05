package com.an.llm.connector.gateway.base;

import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public abstract class BaseApiDelegate {
    public <T> ResponseEntity<@NonNull ApiResponseBody<T>> sendSuccessfulApiResponse(T data, String message){
        ApiResponseBody<T> body = new ApiResponseBody<>();
        body.setCode(HttpStatus.OK.value());
        body.setMessage(message);
        body.setStatus(true);
        body.setData(data);

        return new ResponseEntity<>(body,HttpStatus.OK);
    }

    public <T> ResponseEntity<@NonNull ApiResponseBody<T>> sendSuccessfulApiResponse(T data){
        ApiResponseBody<T> body = new ApiResponseBody<>();
        body.setStatus(true);
        body.setCode(HttpStatus.OK.value());
        body.setMessage("Data fetched successfully.");
        body.setData(data);

        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    public <T> ResponseEntity<@NonNull ApiResponseBody<T>> sendCreatedApiResponse(T data, String message) {
        ApiResponseBody<T> body = new ApiResponseBody<>();
        body.setData(data);
        body.setStatus(true);
        body.setCode(HttpStatus.CREATED.value());
        body.setMessage(message);

        return new ResponseEntity<>(body,HttpStatus.CREATED);
    }

    public <T> ResponseEntity<@NonNull ApiResponseBody<T>> sendCreatedApiResponse(T data){
        ApiResponseBody<T> body = new ApiResponseBody<>();
        body.setStatus(true);
        body.setCode(HttpStatus.CREATED.value());
        body.setMessage("Data created successfully.");

        return new ResponseEntity<>(body,HttpStatus.CREATED);
    }

}
