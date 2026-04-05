package com.an.llm.connector.gateway.config;

import com.an.llm.connector.gateway.security.exception.LlmAccessDeniedHandler;
import com.an.llm.connector.gateway.security.exception.LlmJwtAuthenticationEntryPoint;
import com.an.llm.connector.gateway.security.filter.JwtValidatorFilter;
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
                        .anyRequest().permitAll())
                .addFilterBefore(jwtValidatorFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(Customizer.withDefaults());

        return httpSecurity.build();
    }
}
