package com.an.llm.connector.gateway.security.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Signing key configurations lifted from yaml. This is used for getting the signing key for the JWT token verification.
 * Has configs that ensures where to get the signing key from, either the yaml or the environment variable initialized
 * in hosted server.
 */
@Getter
@Setter
@ToString
@ConfigurationProperties("app.security.key")
public class KeyConfig {
    private String signingSecret;
    private String keyType;
    private boolean useEnv;
    private String keyEnvVar;
}
