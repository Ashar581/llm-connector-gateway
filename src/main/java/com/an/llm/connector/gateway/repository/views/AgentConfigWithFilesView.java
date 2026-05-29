package com.an.llm.connector.gateway.repository.views;

import com.an.llm.connector.gateway.enums.ClassificationMode;
import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.enums.LlmModels;
import com.an.llm.connector.gateway.enums.Source;
import com.an.llm.connector.gateway.model.classification.DocumentTypeDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AgentConfigWithFilesView {
    Long getAgentId();
    String getAgentName();
    Source getSource();
    LlmModels getModel();
    LlmCapability getType();
    String getInstructions();
    UUID getUniqueId();
    Boolean getActive();
    Double getTemperature();
    Integer getMaxTokens();
    Boolean getIsPrivate();
    ClassificationMode getClassificationMode();
    List<DocumentTypeDefinition> getDocumentTypes();
    String getCreatedBy();
    Instant getCreatedAt();
    String getUpdatedBy();
    Instant getUpdatedAt();

    Long getFileId();
    String getFileName();
    String getContentType();
    Map<String, Object> getMetadata();
}
