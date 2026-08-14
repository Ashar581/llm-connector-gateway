package com.an.llm.connector.gateway.security.jwt;

import com.an.llm.connector.gateway.dto.user.UserDto;
import com.an.llm.connector.gateway.security.SecretKeyProvider;
import com.an.llm.connector.gateway.security.config.JwtConfig;
import com.an.llm.connector.gateway.security.constant.SecurityConstants;
import com.an.llm.connector.gateway.security.model.JwtUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtTokenWrapperService implements SecurityConstants {
    private final SecretKeyProvider secretKeyProvider;
    private final JwtConfig jwtConfig;

    public JwtUser validateToken(String token){
        Jws<Claims> jws;
        try{
            jws = Jwts.parserBuilder()
                    .setSigningKey(secretKeyProvider.getKey())
                    .build()
                    .parseClaimsJws(token);

            JwtUser user = new JwtUser();
            Claims body = jws.getBody();

            user.setPrincipal(body.getId());
            user.setFullname(body.getSubject());

            Object rawAuthorities = body.get(AUTHORITIES);
            if (rawAuthorities instanceof List<?>) {
                List<String> authorities = ((List<?>) rawAuthorities)
                        .stream()
                        .map(Object::toString)
                        .collect(Collectors.toList());

                user.setAuthorities(authorities);
            }

            return user;
        }catch (Exception e){
            return null;
        }
    }

    public String generateAccessToken(@NonNull UserDto user) {
        String secret = HttpHeaders.encodeBasicAuth(user.getUsername(),"default", StandardCharsets.UTF_8);
        Map<String, Object> header = Map.of("t", secret, "groups", user.getGroups());
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());

        Instant now= Instant.now();
        Date initiationDate= Date.from(now);
        Instant expiryInstant = now.plus(Duration.ofMinutes(jwtConfig.getAtexpirationtimeinmin()));
        Date expirydate = Date.from(expiryInstant);

        return Jwts.builder()
                .setHeader(header)
                .setSubject(user.getFullname())
                .setId(user.getUsername())
                .setIssuedAt(initiationDate)
                .setExpiration(expirydate)
                .claim("authorities", authorities)
                .claim("groups",header.get("groups"))
                .signWith(secretKeyProvider.getKey())
                .compact();
    }

    public String generateRefreshToken(@NonNull UserDto user){
        String secret = HttpHeaders.encodeBasicAuth(user.getUsername(),"default", StandardCharsets.UTF_8);
        //For Jwt user
        Map<String, Object> header = Map.of("t", secret);

        //date and time for jwt
        Instant now= Instant.now();
        Date initiationDate= Date.from(now);
        Instant expiryInstant = now.plus(Duration.ofMinutes(jwtConfig.getRtexpirationtimeinmin()));
        Date expirydate = Date.from(expiryInstant);

        return Jwts.builder()
                .setHeader(header)
                .setSubject(user.getFullname())
                .setId(user.getUsername())
                .setIssuedAt(initiationDate)
                .setExpiration(expirydate)
                .signWith(secretKeyProvider.getKey())
                .compact();
    }
}
