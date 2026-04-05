package com.an.llm.connector.gateway.security.context;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * Custom authentication class for adding our own data per authenticated request.
 */
public class LlmUsernamePasswordAuthToken extends UsernamePasswordAuthenticationToken {
    private String fullname;
    private List<String> groups;

    public LlmUsernamePasswordAuthToken(Object principal, Object credentials) {
        super(principal, credentials);
    }

    public LlmUsernamePasswordAuthToken(Object principal, Object credentials, Collection<? extends GrantedAuthority> authorities) {
        super(principal, credentials, authorities);
    }

    public LlmUsernamePasswordAuthToken(Object principal, Object credentials, Collection<? extends GrantedAuthority> authorities, List<String> groups, String fullname) {
        super(principal, credentials, authorities);
        this.fullname = fullname;
        this.groups= groups;
    }

    public List<String> getGroups() {
        return this.groups != null ? this.groups : List.of();
    }

    public String getFullname(){
        return this.fullname != null ? this.fullname : "";
    }
}
