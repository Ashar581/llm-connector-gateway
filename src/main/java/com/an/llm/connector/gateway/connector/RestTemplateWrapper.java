package com.an.llm.connector.gateway.connector;

import com.an.llm.connector.gateway.security.auth.ThreadLocalAuthStore;
import com.an.llm.connector.gateway.security.constant.SecurityConstants;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * RestTemplateWrapper is a utility class for wrapping REST API calls.
 * Primarily used for iFlow endpoints but can handle generic REST APIs.
 *
 * <p>Supports GET, POST, PUT, and DELETE requests with:</p>
 * <ul>
 *     <li><b>url</b> : API endpoint, must not be null (e.g., "/bpmnapi/task")</li>
 *     <li><b>parameters</b> : Query parameters as key-value pairs, can be null</li>
 *     <li><b>payload</b> : Request body for POST/PUT, can be null</li>
 *     <li><b>headers</b> : HTTP headers as key-value pairs, can be null</li>
 *     <li><b>returnType</b> : Expected response type, must not be null</li>
 * </ul>
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestTemplateWrapper implements SecurityConstants {
    private final RestTemplate restTemplate;

    public <T> T invokeGetAPI(@NonNull String url, Map<String,String> parameters, Map<String,String> headers, @NonNull ParameterizedTypeReference<T> returnType){
        HttpHeaders httpHeaders = new HttpHeaders();

        addBearerToken(httpHeaders);

        if (headers!=null){
            headers.forEach((k,v)->{
                if (k!=null && v!=null){
                    httpHeaders.set(k,v);
                }
            });
        }

        HttpEntity<Object> httpEntity = new HttpEntity<>(null,httpHeaders);

        URI uri = null;
        try {
            uri = formUri(url,parameters);
            return restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    httpEntity,
                    returnType
            ).getBody();
        }catch (Exception e){
            log.error("Error invoking GET request on URL :: {}",uri,e);
            return null;
        }
    }

    public <T,T1> T invokePostAPI(@NonNull String url, Map<String,String> parameters, T1 payload, Map<String,String> headers, @NonNull ParameterizedTypeReference<T> returnType){
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);

        addBearerToken(httpHeaders);

        if (headers!=null){
            headers.forEach((k,v)->{
                if (k!=null && v!=null){
                    httpHeaders.set(k,v);
                }
            });
        }

        HttpEntity<T1> entity = new HttpEntity<>(payload,httpHeaders);

        URI uri = null;

        try {
            uri = formUri(url,parameters);
            return restTemplate.exchange(
                    uri,
                    HttpMethod.POST,
                    entity,
                    returnType
            ).getBody();
        } catch (Exception e){
            log.error("Error invoking POST request on URL :: {}",uri,e);
            return null;
        }
    }

    public <T,T1> T invokePutAPI(@NonNull String url, Map<String,String> parameters, T1 payload, Map<String,String> headers, @NonNull ParameterizedTypeReference<T> returnType) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);

        addBearerToken(httpHeaders);

        if (headers!=null){
            headers.forEach((k,v)->{
                if (k!=null && v!=null){
                    httpHeaders.set(k,v);
                }
            });
        }

        HttpEntity<T1> entity = new HttpEntity<>(payload,httpHeaders);

        URI uri = null;

        try {
            uri = formUri(url,parameters);
            return restTemplate.exchange(
                    uri,
                    HttpMethod.PUT,
                    entity,
                    returnType
            ).getBody();
        } catch (Exception e){
            log.error("Error invoking PUT request on URL :: {}",uri,e);
            return null;
        }
    }

    public <T,T1> T invokePatchAPI(@NonNull String url, Map<String,String> parameters, T1 payload, Map<String,String> headers, @NonNull ParameterizedTypeReference<T> returnType){
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);

        addBearerToken(httpHeaders);

        if (headers!=null){
            headers.forEach((k,v)->{
                if (k!=null && v!=null){
                    httpHeaders.set(k,v);
                }
            });
        }

        HttpEntity<T1> entity = new HttpEntity<>(payload,httpHeaders);

        URI uri = null;

        try {
            uri = formUri(url,parameters);
            return restTemplate.exchange(
                    uri,
                    HttpMethod.PATCH,
                    entity,
                    returnType
            ).getBody();
        } catch (Exception e){
            log.error("Error invoking PATCH request on URL :: {}",uri,e);
            return null;
        }
    }

    public <T> T invokeDeleteAPI(@NonNull String url, Map<String,String> parameters, Map<String,String> headers, @NonNull ParameterizedTypeReference<T> returnType) {
        HttpHeaders httpHeaders = new HttpHeaders();

        addBearerToken(httpHeaders);

        if (headers!=null){
            headers.forEach((k,v)->{
                if (k!=null && v!=null){
                    httpHeaders.set(k,v);
                }
            });
        }

        HttpEntity<Object> entity = new HttpEntity<>(null,httpHeaders);

        URI uri = null;

        try {
            uri = formUri(url,parameters);
            return restTemplate.exchange(
                    uri,
                    HttpMethod.DELETE,
                    entity,
                    returnType
            ).getBody();
        } catch (Exception e){
            log.error("Error invoking Delete request on URL :: {}",uri,e);
            return null;
        }
    }

    private void addBearerToken(HttpHeaders headers){
        var bearerToken = ThreadLocalAuthStore.getToken();
        if (bearerToken!=null) {
            headers.set(HttpHeaders.AUTHORIZATION, String.format("%s%s",JWT_PREFIX,bearerToken));
        }
    }

    private URI formUri(String url, Map<String,String> params){
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

        if (params != null) {
            params.forEach((k,v) -> {
                if (k != null && v != null) {
                    builder.queryParam(k, v);
                }
            });
        }

        return builder.build().encode().toUri();
    }
}
