package com.an.llm.connector.gateway.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Util class for encoding and decoding of strings. Add new function if needed.
 */
public class HttpUtils {
    public static String generateBasicAuthHeader(String username, String password) {
        if (username != null && password != null) {
            String auth = username + ":" + password;
            return "Basic " + encodeString(auth);
        }
        return "";
    }
    public static String encodeString(String toBeEncoded){
        if (toBeEncoded!=null){
            return new String(Base64.getEncoder().encode(toBeEncoded.getBytes(StandardCharsets.US_ASCII)));
        }
        return "";
    }
}
