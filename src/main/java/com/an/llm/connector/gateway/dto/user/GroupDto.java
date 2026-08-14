package com.an.llm.connector.gateway.dto.user;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupDto {
    private Long id;
    @NotNull(message = "Group name is mandatory.")
    @NotBlank(message = "Group name is mandatory.")
    private String name;
    private String code;
    private String description;

    private List<RoleDto> roles;

    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
}
