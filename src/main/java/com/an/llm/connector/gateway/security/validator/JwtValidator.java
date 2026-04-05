package com.an.llm.connector.gateway.security.validator;

import com.an.llm.connector.gateway.security.SecretKeyProvider;
import com.an.llm.connector.gateway.security.model.JwtUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtValidator {
    private final SecretKeyProvider SecretKeyProvider;

    public JwtUser validateToken(String token){
        try {
            Jws<Claims> jws = Jwts
                    .parserBuilder()
                    .setSigningKey(this.SecretKeyProvider.getKey())
                    .build()
                    .parseClaimsJws(token);

            JwtUser user = new JwtUser();

            Claims body = jws.getBody();

            user.setData(jws.getHeader().get("t"));
            user.setPrincipal(body.getId());
            user.setFullname(body.getSubject());

            Object authorities = body.get("authorities");
            if (authorities instanceof List<?> list){
                user.setAuthorities(list.stream()
                        .filter(Map.class::isInstance)
                        .map(Map.class::cast)
                        .map(a -> (String) a.get("authority"))
                        .filter(Objects::nonNull)
                        .toList());
            } else {
                user.setAuthorities(List.of());
            }

            Object groups = body.get("groups");
            if (groups instanceof List<?> list){
                user.setGroups(list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList());
            } else {
                user.setGroups(List.of());
            }

            return user;
        }catch (Exception e){
            log.error("Error validation token: {}",e.getMessage());
            return null;
        }
    }
}
