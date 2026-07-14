package com.an.llm.connector.gateway.security.exception;

import com.an.llm.connector.gateway.base.ApiExceptionBody;
import com.an.llm.connector.gateway.util.JsonUtils;
import com.an.llm.connector.gateway.util.TimeUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom error response for handling the access denied in case of unauthorized role trying to access
 * an authorized endpoint.
 */
@Component
public class LlmAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, @NonNull AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");

        ApiExceptionBody error = new ApiExceptionBody();
        error.setStatus(false);
        error.setTimestamp(TimeUtils.EXCEPTION_RESPONSE_FORMAT);
        error.setMessage("You are not allowed to access this endpoint.");
        error.setCode(HttpStatus.FORBIDDEN.value());
        error.setPath(request.getRequestURI());

        response.getWriter().write(JsonUtils.serializeClass(error));
    }
}
