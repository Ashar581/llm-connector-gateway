package com.an.llm.connector.gateway.security.model;

import lombok.Data;

import java.util.List;

/**
 * Used to store the current authenticated user's data on each authenticated API call.
 */
@Data
public class JwtUser {
    private String principal;
    private String fullname;
    private List<String> groups;
    private List<String> authorities;
    private Object data;

    public String getPrincipal(){
        return this.principal != null ? this.principal : "System";
    }
}
