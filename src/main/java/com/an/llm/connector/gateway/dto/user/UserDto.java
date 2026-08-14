package com.an.llm.connector.gateway.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.Set;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDto {
    private Long id;
    @NotNull(message = "First name is mandatory.")
    @NotBlank(message = "First name is mandatory.")
    private String firstName;
    @NotNull(message = "Last name is mandatory.")
    @NotBlank(message = "Last name is mandatory.")
    private String lastName;
    private String fullname;
    private String username;
    @NotNull(message = "Email is mandatory.")
    @NotBlank(message = "Email is mandatory.")
    private String email;
    private String password;
    private String phoneNumber;
    private String countryCode;
    private Boolean active;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
    private Set<String> groups;
    private Set<String> roles;

    public String getFullname() {
        return this.firstName + " " + this.lastName;
    }
}
