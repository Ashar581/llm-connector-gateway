package com.an.llm.connector.gateway.security.filter;

import com.an.llm.connector.gateway.base.ApiExceptionBody;
import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.dto.user.UserDto;
import com.an.llm.connector.gateway.exception.AuthenticationFailedException;
import com.an.llm.connector.gateway.model.auth.LoginResponse;
import com.an.llm.connector.gateway.security.jwt.JwtTokenWrapperService;
import com.an.llm.connector.gateway.security.model.JwtUser;
import com.an.llm.connector.gateway.service.user.UserService;
import com.an.llm.connector.gateway.util.JsonUtils;
import com.an.llm.connector.gateway.util.TimeFormats;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public class RefreshTokenFilter extends UsernamePasswordAuthenticationFilter implements TimeFormats {
    private final JwtTokenWrapperService jwtTokenWrapperService;
    private final UserService userService;
    private final JsonUtils jsonUtils;

    public RefreshTokenFilter(JwtTokenWrapperService jwtTokenWrapperService, UserService userService) {
        this.setFilterProcessesUrl("/api/llm/v1/users/auth/refresh-token");
        this.jwtTokenWrapperService = jwtTokenWrapperService;
        this.userService = userService;
        this.jsonUtils = new JsonUtils();
    }

    @NonNull
    @Override
    public Authentication attemptAuthentication(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response) throws AuthenticationException {
        log.info("Attempting authentication using refresh token.");
        if (request.getMethod().equals(HttpMethod.POST.name())) {
            Authentication authentication;
            try {
                LoginResponse loginResponse = jsonUtils.deserialize(request.getInputStream(), LoginResponse.class);
                String refreshToken = loginResponse.getToken() == null ? loginResponse.getRefreshToken() : loginResponse.getToken();

                JwtUser currentUser = jwtTokenWrapperService.validateToken(refreshToken);

                if (currentUser==null) throw new AuthenticationFailedException("Token expired. Login again.");
                authentication = new UsernamePasswordAuthenticationToken(currentUser.getPrincipal(),refreshToken);

                return authentication;
            } catch (Exception e) {
                throw new AuthenticationException("Token expired.") {};
            }
        }

        throw new AuthenticationException("Token expired.") {};
    }

    @Override
    public void successfulAuthentication(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain, @NonNull Authentication authResult) throws IOException {
        log.info("Refresh token validated. Generating new access token and user details.");

        UserDto loggedInUser = userService.view(authResult.getName());
        String accessToken = jwtTokenWrapperService.generateAccessToken(loggedInUser);
        String refreshToken = String.valueOf(authResult.getCredentials());

        LoginResponse loggedIn = LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .user(loggedInUser)
                .build();

        ApiResponseBody<LoginResponse> apiResponse = new ApiResponseBody<>();
        apiResponse.setCode(HttpStatus.OK.value());
        apiResponse.setStatus(true);
        apiResponse.setMessage("Reauthentication completed successfully.");
        apiResponse.setData(loggedIn);

        response.getOutputStream().print(jsonUtils.serialize(apiResponse));
        response.setContentType("application/json");
    }

    @Override
    protected void unsuccessfulAuthentication(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull AuthenticationException failed) throws IOException, ServletException {
        log.info("Refresh token invalid. Authentication failed. New login needed.");
        ApiExceptionBody authFailedResponse = new ApiExceptionBody();
        authFailedResponse.setCode(HttpStatus.UNAUTHORIZED.value());
        authFailedResponse.setMessage(failed.getMessage());
        authFailedResponse.setPath(request.getRequestURI());
        authFailedResponse.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern(ERROR_TIME_FORMAT)));
        authFailedResponse.setStatus(false);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.getOutputStream().print(jsonUtils.serialize(authFailedResponse));
        response.setContentType("application/json");
    }
}