package com.an.llm.connector.gateway.security.auth;

import com.an.llm.connector.gateway.util.HttpUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the authentication data for the current thread. Used in {@link com.an.llm.connector.gateway.connector.RestTemplateWrapper}.
 * Keeps the security level data (tokens) for that thread and can be used accordingly.
 */
@Slf4j
public class ThreadLocalAuthStore {
    private static final ThreadLocal<TokenStore> localThreadTokenStore = new ThreadLocal<>();

    public static void updateAdminTokenFromConfig(String username, String password){
        if (username != null && password != null){
            String basicAuthAdmin = HttpUtils.generateBasicAuthHeader(username,password);

            TokenStore adminToken = getLocalThreadTokenStore();

            if (adminToken == null){
                adminToken= new TokenStore();
                localThreadTokenStore.set(adminToken);
            }
            adminToken.setAdminToken(basicAuthAdmin);
        }
    }

    public static void clearAdminToken(){
        TokenStore adminToken = getLocalThreadTokenStore();
        if (adminToken != null){
            localThreadTokenStore.get().setAdminToken(null);
        }
    }

    public static TokenStore getLocalThreadTokenStore(){
        return localThreadTokenStore.get();
    }

    public static void updateToken(String token){
        TokenStore store = getLocalThreadTokenStore();
        if (store==null){
            store = new TokenStore();
            localThreadTokenStore.set(store);
        }
        store.setBearerToken(token);
    }

    public static String getToken(){
        TokenStore store = getLocalThreadTokenStore();
        if (store!=null){
            return store.getBearerToken();
        }
        return null;
    }

    public static String getUserToken(){
        TokenStore store = getLocalThreadTokenStore();
        if (store!=null){
            return store.getUserToken();
        }
        return null;
    }

    public static void updateUserToken(String userToken){
        TokenStore store = getLocalThreadTokenStore();
        if (store == null){
            store = new TokenStore();
            localThreadTokenStore.set(store);
        }
        store.setUserToken(userToken);
    }

    public static String getAdminToken(){
        TokenStore adminToken = getLocalThreadTokenStore();

        if (adminToken != null){
            return adminToken.getAdminToken();
        }
        return null;
    }

    public static void clearThreadStore(){
        localThreadTokenStore.remove();
    }
}
