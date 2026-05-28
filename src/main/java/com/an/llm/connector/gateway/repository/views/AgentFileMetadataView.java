package com.an.llm.connector.gateway.repository.views;

import java.util.Map;

public interface AgentFileMetadataView {
    Long getId();
    String getFileName();
    String getContentType();
    Map<String, Object> getMetadata();
}
