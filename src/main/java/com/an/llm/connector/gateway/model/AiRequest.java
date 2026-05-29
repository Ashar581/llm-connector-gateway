package com.an.llm.connector.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiRequest {
    @NotBlank(message = "Agent name is mandatory.")
    @NotNull(message = "Agent name is mandatory.")
    private String agent;
    private String model;
    @NotNull(message = "Query is mandatory.")
    private String query;
    private List<MultipartFile> files;
}
