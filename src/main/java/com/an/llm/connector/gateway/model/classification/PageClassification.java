package com.an.llm.connector.gateway.model.classification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageClassification {
    private int pageNumber;
    private String documentType;
    private double confidence;
}
