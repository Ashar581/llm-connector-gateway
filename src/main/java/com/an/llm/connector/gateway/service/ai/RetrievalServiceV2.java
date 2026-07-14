package com.an.llm.connector.gateway.service.ai;

import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.util.FileHashGenerator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceV2 {

    public List<Document> retrieve(@NonNull VectorStore vectorStore, @NonNull LlmConnectorRequest request) {
        if (request.getQuery() == null || request.getQuery().isBlank()) throw new NullException("Chat query is mandatory for generating answer.");

        String filter = null;
        String hashKey = null;
        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            try {
                hashKey = FileHashGenerator.generateSHA256(request.getFiles().getFirst());
            } catch (Exception ignore) {}
        }

        if (request.getAgentName() != null && hashKey != null) {
            filter = "agent == '" + request.getAgentName() + "' && hash == '" + hashKey + "'";
        } else if (request.getAgentName() != null) {
            filter = "agent == '" + request.getAgentName() + "'";
        } else if (hashKey != null) {
            filter = "hash == '" + hashKey + "'";
        }

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(request.getQuery())
                .topK(request.getTopK() != null ? request.getTopK() : 3)
                .similarityThreshold(request.getSimilarityThreshold() != null ? request.getSimilarityThreshold() : 0.3);

        if (filter != null) {
            builder.filterExpression(filter);
        }

        return vectorStore.similaritySearch(builder.build());
    }

    public boolean documentExists(@NonNull VectorStore vectorStore, String hashKey) {
        return !vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("")
                        .topK(1)
                        .filterExpression("hash == '" + hashKey + "'")
                        .build()
        ).isEmpty();
    }

    // Implementation based on in-memory vector store.
    public void addAgentToDocument(@NonNull VectorStore vectorStore, @NonNull String hashKey, @NonNull String agentId) {
        List<Document> chunks = findDocumentChunks(vectorStore, hashKey);

        if (chunks.isEmpty()) {
            return;
        }

        boolean modified = false;

        for (Document chunk : chunks) {
            modified |= addAgentMetadata(chunk, agentId);
        }

        if (modified) {
            vectorStore.add(chunks);
        }
    }

    // Implementation based on in-memory vector store.
    public void removeAgentFromDocument(@NonNull VectorStore vectorStore, @NonNull String hashKey, @NonNull String agentId) {
        List<Document> chunks = findDocumentChunks(vectorStore, hashKey);

        if (chunks.isEmpty()) {
            return;
        }

        boolean modified = false;

        for (Document chunk : chunks) {
            modified |= removeAgentMetadata(chunk, agentId);
        }

        if (modified) {
            vectorStore.add(chunks);
        }
    }

    private List<Document> findDocumentChunks(@NonNull VectorStore vectorStore, @NonNull String hashKey) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("")
                        .topK(1000000)
                        .filterExpression("hash == '" + hashKey + "'")
                        .build());
    }

    private boolean addAgentMetadata(@NonNull Document document, @NonNull String agentId) {
        Map<String, Object> metadata = document.getMetadata();

        String agents = (String) metadata.get("agents");

        if (agents == null || agents.isBlank()) {
            metadata.put("agents", agentId);
            return true;
        }

        // Already associated with this agent.
        boolean exists = Arrays.stream(agents.split(","))
                .map(String::trim)
                .anyMatch(agentId::equals);

        if (exists) {
            log.info("No addition of agents proceeded since metadata chunk had the agent id {}.",agentId);
            return false;
        }

        metadata.put("agents", agents + "," + agentId);
        return true;
    }

    private boolean removeAgentMetadata(@NonNull Document document, @NonNull String agentId) {

        Map<String, Object> metadata = document.getMetadata();

        String agents = (String) metadata.get("agents");
        if (agents == null || agents.isBlank()) {
            return false;
        }

        List<String> updatedAgents = Arrays.stream(agents.split(","))
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .filter(id -> !id.equals(agentId))
                .toList();

        if (updatedAgents.size() == agents.split(",").length) {
            log.info("No removal of agents proceeded since no metadata chunk had the agent id {}.",agentId);
            return false;
        }

        if (updatedAgents.isEmpty()) {
            metadata.remove("agents");
        } else {
            metadata.put("agents", String.join(",", updatedAgents));
        }

        return true;
    }
}
