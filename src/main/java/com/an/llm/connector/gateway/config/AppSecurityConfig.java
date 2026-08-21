package com.an.llm.connector.gateway.config;

import com.an.llm.connector.gateway.security.exception.LlmAccessDeniedHandler;
import com.an.llm.connector.gateway.security.exception.LlmJwtAuthenticationEntryPoint;
import com.an.llm.connector.gateway.security.filter.JwtGeneratorFilter;
import com.an.llm.connector.gateway.security.filter.JwtValidatorFilter;
import com.an.llm.connector.gateway.security.filter.RefreshTokenFilter;
import com.an.llm.connector.gateway.security.jwt.JwtTokenWrapperService;
import com.an.llm.connector.gateway.service.user.AuthenticationService;
import com.an.llm.connector.gateway.service.user.UserService;
import com.an.llm.connector.gateway.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collections;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class AppSecurityConfig {
    private final JwtValidatorFilter jwtValidatorFilter;
    private final JwtTokenWrapperService jwtTokenWrapperService;
    private final AuthenticationService authenticationService;
    private final UserService userService;

    private final LlmAccessDeniedHandler llmAccessDeniedHandler;
    private final LlmJwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception{
        httpSecurity
                .exceptionHandling(handler -> handler
                        .accessDeniedHandler(llmAccessDeniedHandler)
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .cors(cors -> cors
                        .configurationSource(request -> {
                            CorsConfiguration config = new CorsConfiguration();
                            config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE"));
                            config.setExposedHeaders(Collections.singletonList("Authorization"));
                            config.setAllowedOrigins(List.of("*"));
                            config.setAllowedHeaders(Collections.singletonList("*"));

                            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                            source.registerCorsConfiguration("/api/llm/**",config);

                            return source.getCorsConfiguration(request);
                        })
                )
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                            .contentSecurityPolicy(policy -> policy
                                    .policyDirectives("default-src 'self'")
                            )
                        .httpStrictTransportSecurity(transport ->  transport
                                .includeSubDomains(true)
                        )
                )
                .authorizeHttpRequests(authorization -> authorization
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/","/ui/**", "/index.html", "/assets/**", "/favicon.ico","/api/llm/v1/users/auth/login","/api/llm/v1/settings/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new RefreshTokenFilter(jwtTokenWrapperService,userService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtGeneratorFilter(jwtTokenWrapperService,authenticationService,userService),UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtValidatorFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(Customizer.withDefaults());

        return httpSecurity.build();
    }
}
