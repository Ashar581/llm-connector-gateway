package com.an.llm.connector.gateway.security.auth;

import lombok.Data;

/**
 * Actual Token storage object for {@link ThreadLocalAuthStore}
 */
@Data
public class TokenStore {
    private String userToken;
    private String bearerToken;
    private String adminToken;
}
