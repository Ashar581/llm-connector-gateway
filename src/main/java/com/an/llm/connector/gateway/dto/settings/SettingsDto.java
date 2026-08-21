package com.an.llm.connector.gateway.dto.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SettingsDto {
    private Long id;
    @NotNull(message = "Setting label is mandatory.")
    private String label;
    @NotNull(message = "Setting route path is mandatory.")
    private String routePath;
    private Set<String> roles;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
}
