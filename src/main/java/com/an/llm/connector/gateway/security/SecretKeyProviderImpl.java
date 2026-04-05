package com.an.llm.connector.gateway.security;

import com.an.llm.connector.gateway.security.config.KeyConfig;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecretKeyProviderImpl implements SecretKeyProvider {
    private final KeyConfig keyConfig;
    private final ApplicationContext applicationContext;
    private final Environment environment;

    private Key secretKey;


    @PostConstruct
    private void init(){
        String signingSecret;
        boolean shared = false;

        if (this.keyConfig.getKeyType()!=null){
            shared = this.keyConfig.getKeyType().equalsIgnoreCase("shared");
        }

        if (this.keyConfig.isUseEnv()){
            log.info("Using environment variable for jwt signing key.");
            signingSecret = environment.getProperty(this.keyConfig.getKeyEnvVar(),"");
            if (signingSecret.isBlank()){
                log.error("Secret key not found in environment variable. Please add and restart the service.");
                ((ConfigurableApplicationContext)this.applicationContext).close();
            }
        } else {
            log.info("Using yml for jwt signing key.");
            signingSecret = this.keyConfig.getSigningSecret();
        }

        if (shared){
            this.secretKey = Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8));
        } else {
            this.secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
        }
    }

    @Override
    public Key getKey() {
        return this.secretKey;
    }
}
