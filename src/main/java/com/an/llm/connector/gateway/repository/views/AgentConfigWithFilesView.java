package com.an.llm.connector.gateway.repository.views;

import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.enums.LlmModels;
import com.an.llm.connector.gateway.enums.Source;

import java.time.Instant;
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
    String getCreatedBy();
    Instant getCreatedAt();
    String getUpdatedBy();
    Instant getUpdatedAt();

    Long getFileId();
    String getFileName();
    String getContentType();
    Map<String, Object> getMetadata();
}
