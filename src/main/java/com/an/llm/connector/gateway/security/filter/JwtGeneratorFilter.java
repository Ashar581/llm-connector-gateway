package com.an.llm.connector.gateway.security.filter;

import com.an.llm.connector.gateway.base.ApiExceptionBody;
import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.dto.user.UserDto;
import com.an.llm.connector.gateway.exception.AuthenticationFailedException;
import com.an.llm.connector.gateway.model.auth.LoginRequest;
import com.an.llm.connector.gateway.model.auth.LoginResponse;
import com.an.llm.connector.gateway.security.jwt.JwtTokenWrapperService;
import com.an.llm.connector.gateway.service.user.AuthenticationService;
import com.an.llm.connector.gateway.service.user.UserService;
import com.an.llm.connector.gateway.util.JsonUtils;
import com.an.llm.connector.gateway.util.TimeFormats;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
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
public class JwtGeneratorFilter extends UsernamePasswordAuthenticationFilter implements TimeFormats {
    private final JwtTokenWrapperService jwtTokenWrapperService;
    private final AuthenticationService authenticationService;
    private final UserService userService;
    private final JsonUtils jsonUtils = new JsonUtils();

    public JwtGeneratorFilter(JwtTokenWrapperService jwtTokenWrapperService, AuthenticationService authenticationService, UserService userService){
        this.setFilterProcessesUrl("/api/llm/v1/users/auth/login");
        this.jwtTokenWrapperService = jwtTokenWrapperService;
        this.authenticationService = authenticationService;
        this.userService = userService;
    }

    @NotNull
    @Override
    public Authentication attemptAuthentication(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response) throws AuthenticationException {
        log.info("Attempting authentication.");
        if (request.getMethod().equals(HttpMethod.POST.name())) {
            Authentication authentication;
            try {
                LoginRequest credentials = jsonUtils.deserialize(request.getInputStream(), LoginRequest.class);
                credentials.setUserid(credentials.getUserid().trim().toLowerCase());

                //authenticate using user service after the service code has been completed.
                boolean userAuthenticated = authenticationService.authenticate(credentials);

                if (!userAuthenticated) {
                    throw new AuthenticationException("Wrong username or password.") {};
                }
                authentication = new UsernamePasswordAuthenticationToken(credentials.getUserid(), credentials.getPassword());
                return authentication;
            } catch (Exception e) {
                throw new AuthenticationException("Wrong username or password.") {};
            }
        }
        throw new AuthenticationException("Unauthorized access.") {};
    }

    @Override
    public void successfulAuthentication(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain, Authentication authResult) throws IOException {
        log.info("Authentication successful. Generating response for authenticated user.");
        String userId = authResult.getName();

        UserDto loggedInUser = userService.view(userId);
        String accessToken = jwtTokenWrapperService.generateAccessToken(loggedInUser);
        String refreshToken = jwtTokenWrapperService.generateRefreshToken(loggedInUser);

        //dummy data for testing.
        LoginResponse loggedIn = LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .user(loggedInUser)
                .build();

        //get the user details.
        //generate the token and refresh token
        //set the response.
        ApiResponseBody<LoginResponse> apiResponse = new ApiResponseBody<>();
        apiResponse.setCode(HttpStatus.OK.value());
        apiResponse.setStatus(true);
        apiResponse.setMessage("User logged in successfully.");
        apiResponse.setData(loggedIn);

        response.getOutputStream().print(jsonUtils.serialize(apiResponse));
        response.setContentType("application/json");
    }

    @Override
    protected void unsuccessfulAuthentication(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, AuthenticationException failed) throws IOException, ServletException {
        log.info("Authentication unsuccessful. Generating unauthorized response.");
        //generate the response for unsuccessful authentication.
        //optionally i can also work on updating the database of user table to update number of attempts
        //maybe for account lock.
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
