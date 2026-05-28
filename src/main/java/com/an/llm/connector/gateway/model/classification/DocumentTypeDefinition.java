package com.an.llm.connector.gateway.model.classification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTypeDefinition {
    private String id;
    private String description;
    private List<String> keywords;
}