package com.an.llm.connector.gateway.model.classification;

import com.an.llm.connector.gateway.enums.ClassificationMode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ClassificationResponse {
    private ClassificationMode mode;
    private List<PageAnalysisResult> pages;
    private List<DocumentGroup> documents;
    private String documentType;
    private Double confidence;
}
