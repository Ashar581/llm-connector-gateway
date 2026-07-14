package com.an.llm.connector.gateway.dto;

import com.an.llm.connector.gateway.enums.ClassificationMode;
import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.enums.LlmModels;
import com.an.llm.connector.gateway.enums.Source;
import com.an.llm.connector.gateway.model.classification.DocumentTypeDefinition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfigurationDto {
    private Long id;
    @NotBlank(message = "Agent source is mandatory.")
    @NotNull(message = "Agent source is mandatory.")
    private String source;
    @NotBlank(message = "Agent type is mandatory.")
    @NotNull(message = "Agent type is mandatory.")
    private String type;
    @NotBlank(message = "Agent model is mandatory.")
    @NotNull(message = "Agent model is mandatory.")
    private String model;
    @NotBlank(message = "Agent instructions is mandatory.")
    @NotNull(message = "Agent instructions is mandatory.")
    private String instructions;
    @NotNull(message = "Agent description is mandatory.")
    @NotBlank(message = "Agent description is mandatory.")
    private String description;
    private List<AgentFileDto> files;
    private UUID uniqueId;
    private String name;
    private Boolean active;
    private Double temperature;
    private Integer maxTokens;
    private Boolean isPrivate;
    private ClassificationMode classificationMode;
    private List<DocumentTypeDefinition> documentTypes;
    private Integer pageChunk;
    //RAG based keys.
    private String vectorStore;
    private String encodingType;
    private Integer chunkSize;
    private Integer minChunkLengthToEmbed;
    private Integer minChunkSizeChars;
    private Integer maxNumChunks;
    private Boolean separator;

    private Integer topK;
    private Double similarityThreshold;

    private Boolean enablePrivateMode;

    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
