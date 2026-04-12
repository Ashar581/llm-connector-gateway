package com.an.llm.connector.gateway.service;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConfidenceService {

    public boolean hasUsableContext(List<Document> documents) {
        return documents != null && !documents.isEmpty();
    }

    public boolean isAnswerUnknown(String answer) {
        if (answer == null || answer.isBlank()) {
            return true;
        }

        String normalized = answer.trim().toLowerCase();

        return normalized.contains("unknown")
                || normalized.contains("i don't know")
                || normalized.contains("i do not know")
                || normalized.contains("not enough information")
                || normalized.contains("cannot determine");
    }
}