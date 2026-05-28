package com.an.llm.connector.gateway.model.classification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageAnalysisResult {
    private int pageNumber;
    private String documentType;
    private boolean newDocument;
    private boolean blankPage;
    private double confidence;
}