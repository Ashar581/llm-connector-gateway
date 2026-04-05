package com.an.llm.connector.gateway.security.model;

import lombok.Data;

import java.util.List;

/**
 * Used to store the current authenticated user's data on each authenticated API call.
 */
@Data
public class JwtUser {
    private String principal;
    private List<String> groups;
    private List<String> authorities;
    private String fullname;
    private Object data;

    public String getPrincipal(){
        return this.principal != null ? this.principal : "Anonymous";
    }
}
