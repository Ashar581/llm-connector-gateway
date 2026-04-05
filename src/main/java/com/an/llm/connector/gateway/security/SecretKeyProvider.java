package com.an.llm.connector.gateway.security;

import java.security.Key;

public interface SecretKeyProvider {
    Key getKey();
}
