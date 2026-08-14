package com.an.llm.connector.gateway.exception.handler;

import com.an.llm.connector.gateway.base.ApiExceptionBody;
import com.an.llm.connector.gateway.exception.*;
import com.an.llm.connector.gateway.util.TimeUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;

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

    @ExceptionHandler(NullException.class)
    public ResponseEntity<@NonNull ApiExceptionBody> nullExceptionHandler(NullException nullException, HttpServletRequest request) {
        ApiExceptionBody exception  = new ApiExceptionBody();

        exception.setPath(request.getRequestURI());
        exception.setMessage(nullException.getMessage());
        exception.setStatus(false);
        exception.setTimestamp(EXCEPTION_RESPONSE_FORMAT);
        exception.setCode(HttpStatus.EXPECTATION_FAILED.value());

        return new ResponseEntity<>(exception, HttpStatus.EXPECTATION_FAILED);
    }

    @ExceptionHandler(NotActivatedException.class)
    public ResponseEntity<@NonNull ApiExceptionBody> notActivateExceptionHandler(NotActivatedException notActivatedException, HttpServletRequest request) {
        ApiExceptionBody exception  = new ApiExceptionBody();

        exception.setPath(request.getRequestURI());
        exception.setMessage(notActivatedException.getMessage());
        exception.setStatus(false);
        exception.setTimestamp(EXCEPTION_RESPONSE_FORMAT);
        exception.setCode(HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS.value());

        return new ResponseEntity<>(exception, HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS);
    }

    @ExceptionHandler(NotAvailableException.class)
    public ResponseEntity<@NonNull ApiExceptionBody> notActivateExceptionHandler(NotAvailableException notAvailableException, HttpServletRequest request) {
        ApiExceptionBody exception  = new ApiExceptionBody();

        exception.setPath(request.getRequestURI());
        exception.setMessage(notAvailableException.getMessage());
        exception.setStatus(false);
        exception.setTimestamp(EXCEPTION_RESPONSE_FORMAT);
        exception.setCode(HttpStatus.SERVICE_UNAVAILABLE.value());

        return new ResponseEntity<>(exception, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(NotAllowedException.class)
    public ResponseEntity<@NonNull ApiExceptionBody> notAllowedExceptionHandler(NotAllowedException notAllowedException, HttpServletRequest request) {
        ApiExceptionBody exception  = new ApiExceptionBody();

        exception.setPath(request.getRequestURI());
        exception.setMessage(notAllowedException.getMessage());
        exception.setStatus(false);
        exception.setTimestamp(EXCEPTION_RESPONSE_FORMAT);
        exception.setCode(HttpStatus.METHOD_NOT_ALLOWED.value());

        return new ResponseEntity<>(exception, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<@NonNull ApiExceptionBody> methodArgumentNotValidExceptionHandler(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        String errorMessage = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .findFirst()
                .orElse("Validation failed");

        ApiExceptionBody apiException = new ApiExceptionBody();

        apiException.setPath(request.getRequestURI());
        apiException.setMessage(errorMessage);
        apiException.setStatus(false);
        apiException.setTimestamp(EXCEPTION_RESPONSE_FORMAT);
        apiException.setCode(HttpStatus.BAD_REQUEST.value());

        return new ResponseEntity<>(apiException, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(OperationFailedException.class)
    public ResponseEntity<@NonNull ApiExceptionBody> operationFailedExceptionHandler(OperationFailedException operationFailedException, HttpServletRequest request) {
        ApiExceptionBody exception  = new ApiExceptionBody();

        exception.setPath(request.getRequestURI());
        exception.setMessage(operationFailedException.getMessage());
        exception.setStatus(false);
        exception.setTimestamp(EXCEPTION_RESPONSE_FORMAT);
        exception.setCode(HttpStatus.UNPROCESSABLE_CONTENT.value());

        return new ResponseEntity<>(exception, HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @ExceptionHandler(WebSearchException.class)
    public ResponseEntity<@NonNull ApiExceptionBody> webSearchExceptionHandler(WebSearchException webSearchException, HttpServletRequest request) {
        ApiExceptionBody exception  = new ApiExceptionBody();

        exception.setPath(request.getRequestURI());
        exception.setMessage(webSearchException.getMessage());
        exception.setStatus(false);
        exception.setTimestamp(EXCEPTION_RESPONSE_FORMAT);
        exception.setCode(HttpStatus.BAD_GATEWAY.value());

        return new ResponseEntity<>(exception, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<@NonNull ApiExceptionBody> webSearchExceptionHandler(AuthenticationFailedException authenticationFailedException, HttpServletRequest request) {
        ApiExceptionBody exception  = new ApiExceptionBody();

        exception.setPath(request.getRequestURI());
        exception.setMessage(authenticationFailedException.getMessage());
        exception.setStatus(false);
        exception.setTimestamp(EXCEPTION_RESPONSE_FORMAT);
        exception.setCode(HttpStatus.UNAUTHORIZED.value());

        return new ResponseEntity<>(exception, HttpStatus.UNAUTHORIZED);
    }
}
