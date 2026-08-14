package com.an.llm.connector.gateway.model.auth;

import com.an.llm.connector.gateway.dto.user.UserDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties
public class LoginResponse {
    private UserDto user;
    private String token;
    private String refreshToken;
}
