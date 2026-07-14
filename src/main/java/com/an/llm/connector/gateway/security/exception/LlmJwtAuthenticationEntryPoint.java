package com.an.llm.connector.gateway.security.exception;

import com.an.llm.connector.gateway.base.ApiExceptionBody;
import com.an.llm.connector.gateway.util.JsonUtils;
import com.an.llm.connector.gateway.util.TimeUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom error response for handling the failed validation of the JWT.
 */
@Component
public class LlmJwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, @NonNull AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");

        ApiExceptionBody error = new ApiExceptionBody();
        error.setStatus(false);
        error.setMessage("Authentication failed.");
        error.setCode(HttpStatus.UNAUTHORIZED.value());
        error.setTimestamp(TimeUtils.EXCEPTION_RESPONSE_FORMAT);
        error.setPath(request.getRequestURI());

        response.getWriter().write(JsonUtils.serializeClass(error));
    }
}
