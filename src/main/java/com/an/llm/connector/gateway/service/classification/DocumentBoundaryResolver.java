package com.an.llm.connector.gateway.service.classification;

import com.an.llm.connector.gateway.model.classification.DocumentGroup;
import com.an.llm.connector.gateway.model.classification.PageAnalysisResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentBoundaryResolver {
    private static final String UNKNOWN = "UNKNOWN";

    public List<DocumentGroup> resolve(List<PageAnalysisResult> pages) {
        List<DocumentGroup> groups = new ArrayList<>();

        if (pages == null || pages.isEmpty()) {
            return groups;
        }

        DocumentGroup current = null;

        for (int i = 0; i < pages.size(); i++) {
            PageAnalysisResult page = pages.get(i);

            boolean startNew;

            if (i == 0) {
                startNew = true;
            } else if (page.isBlankPage()) {
                startNew = false;
            } else {
                startNew = page.isNewDocument();

                if (!startNew) {
                    PageAnalysisResult previous = pages.get(i - 1);
                    boolean strongTypeChange =
                            page.getConfidence() >= 0.90
                                    && !UNKNOWN.equals(page.getDocumentType())
                                    && !page.getDocumentType().equals(previous.getDocumentType());

                    if (strongTypeChange) {
                        startNew = true;
                    }
                }
            }

            if (startNew) {
                current = new DocumentGroup();
                current.setDocumentType(page.getDocumentType());
                current.setStartPage(page.getPageNumber());
                current.setEndPage(page.getPageNumber());
                current.setConfidence(page.getConfidence());
                groups.add(current);
                continue;
            }

            current.setEndPage(page.getPageNumber());
            current.setConfidence(Math.max(current.getConfidence(), page.getConfidence()));

            if (page.getConfidence() >= 0.90
                    && !UNKNOWN.equals(page.getDocumentType())
                    && !page.getDocumentType().equals(current.getDocumentType())
                    && !page.isBlankPage()) {
                current.setDocumentType(page.getDocumentType());
            }
        }

        return groups;
    }
}