package com.an.llm.connector.gateway.model;

import com.an.llm.connector.gateway.enums.ClassificationMode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmConnectorRequest {
    @NotNull(message = "Model source is mandatory.")
    private String source;
    @NotNull(message = "Model type is mandatory.")
    private String type;
    @NotNull(message = "Model name is mandatory.")
    private String model;
    private String instructions;
    private String query;
    private List<MultipartFile> files;
    private Double temperature;
    private Integer maxTokens;
    @NotNull(message = "Classification mode is mandatory.")
    private ClassificationMode mode;
//    private List<DocumentTypeDefinition> documentTypes;
    private String documentTypes;
}
