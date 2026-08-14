package com.an.llm.connector.gateway.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("app.security.jwt")
public class JwtConfig {
    private String audience;
    private String issuer;
    private long atexpirationtimeinmin;
    private long rtexpirationtimeinmin;
}
