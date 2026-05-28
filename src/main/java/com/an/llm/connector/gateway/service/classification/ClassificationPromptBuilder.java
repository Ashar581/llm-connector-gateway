package com.an.llm.connector.gateway.service.classification;

import com.an.llm.connector.gateway.model.classification.DocumentTypeDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClassificationPromptBuilder {

    public String build(List<DocumentTypeDefinition> definitions, String extraInstructions) {
        StringBuilder builder = new StringBuilder();

        builder.append("""
            You are an enterprise document classification engine.

            You will receive exactly ONE image: the CURRENT page.
            There is NO previous page context.

            Your task:
            1. Determine the semantic document type of the current page.
            2. Determine whether the current page starts a new document.
            3. Detect whether the page is mostly blank, stamp-only, or signature-only.

            Rules:
            - Classify ONLY the current page image.
            - Never inherit a document type from any other page.
            - If the page has a clear title/header, use that as the strongest signal.
            - If the page is mostly stamps, signatures, seals, or empty space with no real header, set blankPage=true.
            - If unsure, return UNKNOWN.
            - Return STRICT JSON ONLY.
            - Do not explain.

            Supported document types:
            """);

        for (DocumentTypeDefinition def : definitions) {
            builder.append("\n- ")
                    .append(def.getId())
                    .append(": ")
                    .append(def.getDescription());

            if (def.getKeywords() != null && !def.getKeywords().isEmpty()) {
                builder.append("\n  Keywords: ")
                        .append(String.join(", ", def.getKeywords()));
            }
        }

        if (extraInstructions != null && !extraInstructions.trim().isEmpty()) {
            builder.append("\n\nAdditional instructions:\n")
                    .append(extraInstructions.trim());
        }

        builder.append("""

            Return JSON in this exact format:

            {
              "documentType": "",
              "isNewDocument": true,
              "blankPage": false,
              "confidence": 0.0
            }
            """);

        return builder.toString();
    }
}