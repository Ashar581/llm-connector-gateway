package com.an.llm.connector.gateway.security.filter;


import com.an.llm.connector.gateway.security.auth.ThreadLocalAuthStore;
import com.an.llm.connector.gateway.security.constant.SecurityConstants;
import com.an.llm.connector.gateway.security.context.LlmUsernamePasswordAuthToken;
import com.an.llm.connector.gateway.security.model.JwtUser;
import com.an.llm.connector.gateway.security.validator.JwtValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Core validation filter for JWT validation per API request.
 */
@Component
@RequiredArgsConstructor
public class JwtValidatorFilter extends OncePerRequestFilter implements SecurityConstants {
    private final JwtValidator jwtValidator;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String header = request.getHeader(EXPOSED_HEADER);
            if (header != null && header.startsWith(JWT_PREFIX)){
                String token = header.replace(JWT_PREFIX,"");

                JwtUser user = jwtValidator.validateToken(token);

                if (user != null){
                    Authentication authentication = new LlmUsernamePasswordAuthToken(
                            user.getPrincipal(),
                            user.getData(),
                            user.getAuthorities().stream().map(SimpleGrantedAuthority::new).toList(),
                            user.getGroups(),
                            user.getFullname()
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    ThreadLocalAuthStore.updateToken(token);
                    ThreadLocalAuthStore.updateUserToken(String.valueOf(user.getData()));
                }
            }
            filterChain.doFilter(request,response);
        }catch (Exception e){
            filterChain.doFilter(request,response);
        }finally {
            ThreadLocalAuthStore.clearThreadStore();
        }
    }
}
