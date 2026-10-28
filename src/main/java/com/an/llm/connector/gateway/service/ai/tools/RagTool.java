package com.an.llm.connector.gateway.service.ai.tools;

import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.ai.RagServiceV5;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.BeanUtils;

@Slf4j
public record RagTool(RagServiceV5 ragService, LlmConnectorRequest baseRequest) {

    @Tool(description = """
            Search the internal knowledge base for relevant documents.
            Use this tool when answering requires internal or company-specific
            information that is not already available in the conversation.
            Returns document excerpts, not a generated final answer.
            """)
    public String searchKnowledgeBase(@ToolParam(description = "A specific question to search for") String query) {
        log.info("Using RAG agent to retrieve document with query: {}",query);
        if (query == null || query.isBlank()) {
            return "A non-empty search query is required.";
        }

        LlmConnectorRequest toolRequest = new LlmConnectorRequest();
        BeanUtils.copyProperties(baseRequest, toolRequest);
        toolRequest.setQuery(query);

        return ragService.retrieveContext(toolRequest);
    }
}