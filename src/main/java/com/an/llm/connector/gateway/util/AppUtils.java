package com.an.llm.connector.gateway.util;

import com.an.llm.connector.gateway.security.context.LlmUsernamePasswordAuthToken;
import com.an.llm.connector.gateway.security.model.JwtUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A util clas which acts as a helper class for getting authenticated user's data when required. Add new
 * functions if needed.
 */
@Slf4j
public class AppUtils {
    public static JwtUser getLoggedInUser(){
        try {
            JwtUser user = new JwtUser();
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication instanceof LlmUsernamePasswordAuthToken auth){
                user.setFullname(auth.getFullname());
                user.setGroups(auth.getGroups());
            }
            user.setAuthorities(authentication.getAuthorities()
                    .stream()
                    .map(String::valueOf)
                    .toList()
            );
            user.setData(authentication.getCredentials());
            user.setPrincipal(String.valueOf(authentication.getPrincipal()));
            return user;
        }catch (Exception e){
            log.info("Error getting logged in user : {}",e.getMessage());
            return new JwtUser();
        }
    }
}
