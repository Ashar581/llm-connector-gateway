package com.an.llm.connector.gateway.model.classification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentGroup {
    private String documentType;
    private int startPage;
    private int endPage;
    private double confidence;
}
