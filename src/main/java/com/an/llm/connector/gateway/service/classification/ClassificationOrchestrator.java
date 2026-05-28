package com.an.llm.connector.gateway.service.classification;

import com.an.llm.connector.gateway.enums.ClassificationMode;
import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.classification.DocumentGroup;
import com.an.llm.connector.gateway.model.classification.ClassificationResponse;
import com.an.llm.connector.gateway.model.classification.PageAnalysisResult;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassificationOrchestrator {
    private static final String UNKNOWN = "UNKNOWN";
    private static final double SINGLE_DOCUMENT_THRESHOLD = 0.80;
    private static final double MIN_SINGLE_CONFIDENCE = 0.50;

    private final PageAnalysisService pageAnalysisService;
    private final DocumentBoundaryResolver boundaryResolver;

    public ClassificationResponse process(@NonNull LlmConnectorRequest request) throws Exception {
        validateRequest(request);
        return switch (request.getMode()) {
            case SINGLE -> handleSingle(request);
            case PAGE -> handlePage(request);
            case AUTO -> handleAuto(request);
        };
    }

    private ClassificationResponse handleSingle(LlmConnectorRequest request) throws Exception {
        log.info("Handling document classification in Single mode.");
        List<PageAnalysisResult> pages = pageAnalysisService.sampleAndAnalyze(request);

        if (pages.isEmpty()) {
            return emptySingleResponse();
        }

        String dominantType = resolveDominantType(pages);

        double confidence = calculateConfidence(pages);

        if (UNKNOWN.equals(dominantType) || confidence < MIN_SINGLE_CONFIDENCE) {
            dominantType = UNKNOWN;
        }

        return ClassificationResponse.builder()
                .mode(ClassificationMode.SINGLE)
                .documentType(dominantType)
                .confidence(confidence)
                .pages(pages)
                .build();
    }

    private ClassificationResponse handlePage(LlmConnectorRequest request) throws Exception {
        log.info("Handling document classification in Page mode.");
        List<PageAnalysisResult> pages = pageAnalysisService.analyzeAllPages(request);
        List<DocumentGroup> groups = boundaryResolver.resolve(pages);

        return ClassificationResponse.builder()
                .mode(ClassificationMode.PAGE)
                .pages(pages)
                .documents(groups)
                .build();
    }

    private ClassificationResponse handleAuto(LlmConnectorRequest request) throws Exception {
        log.info("Handling document classification in Auto mode.");
        List<PageAnalysisResult> pages = pageAnalysisService.analyzeAllPages(request);

        if (pages.isEmpty()) {
            return emptyPageResponse();
        }
        List<DocumentGroup> groups = boundaryResolver.resolve(pages);

        if (groups.isEmpty()) {
            return emptyPageResponse();
        }

        if (groups.size() == 1) {
            DocumentGroup group = groups.getFirst();

            return ClassificationResponse.builder()
                    .mode(ClassificationMode.SINGLE)
                    .documentType(group.getDocumentType())
                    .confidence(group.getConfidence())
                    .pages(pages)
                    .documents(groups)
                    .build();
        }

        boolean mostlySameType = isMostlySingleType(groups);

        if (mostlySameType) {
            String dominantType = resolveDominantGroupType(groups);

            double confidence = calculateGroupConfidence(groups);

            return ClassificationResponse.builder()
                    .mode(ClassificationMode.SINGLE)
                    .documentType(dominantType)
                    .confidence(confidence)
                    .pages(pages)
                    .documents(groups)
                    .build();
        }

        return ClassificationResponse.builder()
                .mode(ClassificationMode.PAGE)
                .pages(pages)
                .documents(groups)
                .build();
    }

    private String resolveDominantType(List<PageAnalysisResult> pages) {
        return pages.stream()
                .filter(page -> !UNKNOWN.equals(page.getDocumentType()))
                .collect(
                        Collectors.groupingBy(
                                PageAnalysisResult::getDocumentType,
                                Collectors.counting()
                        )
                )
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(UNKNOWN);
    }

    private double calculateConfidence(List<PageAnalysisResult> pages) {
        DoubleSummaryStatistics stats = pages.stream()
                        .filter(page -> !UNKNOWN.equals(page.getDocumentType()))
                        .mapToDouble(PageAnalysisResult::getConfidence)
                        .summaryStatistics();

        if (stats.getCount() == 0) {
            return 0.0;
        }

        return round(stats.getAverage());
    }

    private double calculateGroupConfidence(List<DocumentGroup> groups) {

        double confidence = groups.stream()
                .mapToDouble(DocumentGroup::getConfidence)
                        .average()
                        .orElse(0.0);

        return round(confidence);
    }

    private boolean isMostlySingleType(List<DocumentGroup> groups) {
        Map<String, Long> counts = groups.stream()
                        .filter(group -> !UNKNOWN.equals(group.getDocumentType()))
                        .collect(
                                Collectors.groupingBy(
                                        DocumentGroup::getDocumentType,
                                        Collectors.counting()
                                )
                        );

        if (counts.isEmpty()) {
            return false;
        }

        long total = counts.values()
                        .stream()
                        .mapToLong(Long::longValue)
                        .sum();

        long dominant = counts.values()
                        .stream()
                        .max(Long::compareTo)
                        .orElse(0L);

        double ratio = (double) dominant / total;
        return ratio >= SINGLE_DOCUMENT_THRESHOLD;
    }

    private String resolveDominantGroupType(List<DocumentGroup> groups) {
        return groups.stream()
                .filter(group -> !UNKNOWN.equals(group.getDocumentType()))
                .collect(
                        Collectors.groupingBy(
                                DocumentGroup::getDocumentType,
                                Collectors.counting()
                        )
                )
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(UNKNOWN);
    }

    private void validateRequest(LlmConnectorRequest request) {
        if (request == null) throw new NullException("Invalid request detected.");
        if (request.getInstructions() == null || request.getInstructions().isBlank()) throw new NullException("Agent does not have instructions.");
        if (request.getModel() == null) throw new NullException("Model is mandatory.");
        if (request.getType() == null) throw new NullException("Model Type is mandatory.");
        if (request.getFiles() == null || request.getFiles().isEmpty()) throw new NullException("File is mandatory.");
        if (request.getMode() == null) throw new NullException("Classification mode is mandatory.");
    }

    private ClassificationResponse emptySingleResponse() {
        return ClassificationResponse.builder()
                .mode(ClassificationMode.SINGLE)
                .documentType(UNKNOWN)
                .confidence(0.0)
                .build();
    }

    private ClassificationResponse emptyPageResponse() {
        return ClassificationResponse.builder()
                .mode(ClassificationMode.PAGE)
                .documents(List.of())
                .pages(List.of())
                .build();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}