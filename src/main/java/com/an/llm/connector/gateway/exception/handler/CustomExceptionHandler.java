package com.an.llm.connector.gateway.exception.handler;

import com.an.llm.connector.gateway.base.ApiExceptionBody;
import com.an.llm.connector.gateway.exception.AlreadyExistsException;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.exception.NotFoundException;
import com.an.llm.connector.gateway.util.TimeUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class CustomExceptionHandler implements TimeUtils {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<@NonNull ApiExceptionBody> fallbackExceptionHandler(Exception e, HttpServletRequest request){
        ApiExceptionBody exception = new ApiExceptionBody();

        exception.setStatus(false);
        exception.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        exception.setMessage("Something went wrong internally. Please try again.");
        exception.setPath(request.getRequestURI());
        exception.setTimestamp(EXCEPTION_RESPONSE_FORMAT);

        log.error("Error during calling {}",request.getRequestURI(),e);
        return new ResponseEntity<>(exception, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<@NonNull ApiExceptionBody> notFoundExceptionHandler(NotFoundException notFoundException, HttpServletRequest request){
        ApiExceptionBody exception = new ApiExceptionBody();

        exception.setStatus(false);
        exception.setTimestamp(EXCEPTION_RESPONSE_FORMAT);
        exception.setCode(HttpStatus.NOT_FOUND.value());
        exception.setPath(request.getRequestURI());
        exception.setMessage(notFoundException.getMessage());

        return new ResponseEntity<>(exception, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AlreadyExistsException.class)
    public ResponseEntity<@NonNull ApiExceptionBody> alreadyExistsExceptionHandler(AlreadyExistsException alreadyExistsException, HttpServletRequest request) {
        ApiExceptionBody exception = new ApiExceptionBody();

        exception.setStatus(false);
        exception.setTimestamp(EXCEPTION_RESPONSE_FORMAT);
        exception.setMessage(alreadyExistsException.getMessage());
        exception.setCode(HttpStatus.CONFLICT.value());
        exception.setPath(request.getRequestURI());

        return new ResponseEntity<>(exception,HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ApiFallbackException.class)
    public ResponseEntity<@NonNull ApiExceptionBody> apiFallbackExceptionHandler(ApiFallbackException apiFallbackException, HttpServletRequest request) {
        ApiExceptionBody exception  = new ApiExceptionBody();

        exception.setPath(request.getRequestURI());
        exception.setMessage(apiFallbackException.getMessage());
        exception.setStatus(false);
        exception.setTimestamp(EXCEPTION_RESPONSE_FORMAT);
        exception.setCode(HttpStatus.SERVICE_UNAVAILABLE.value());

        return new ResponseEntity<>(exception, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
