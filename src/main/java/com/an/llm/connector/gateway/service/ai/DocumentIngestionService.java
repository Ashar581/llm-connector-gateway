package com.an.llm.connector.gateway.service.ai;

import com.an.llm.connector.gateway.exception.ApiFallbackException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentIngestionService {
    private final VectorStore vectorStore;
    private final TokenTextSplitter tokenTextSplitter;

    public void ingest(List<String> rawDocuments){
        List<Document> documents = rawDocuments.stream()
                .map(Document::new)
                .toList();

        vectorStore.add(documents);
    }

    public String ingest(MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename()
                    : "unknown-file";

            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return originalFilename;
                }
            };

            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.get();

            List<Document> cleanedDocs = documents.stream()
                    .map(doc -> new Document(
                            cleanText(doc.getText()),
                            mergeMetadata(doc.getMetadata(), originalFilename)
                    ))
                    .toList();

            List<Document> paragraphDocs = new ArrayList<>();
            for (Document document : cleanedDocs) {
                paragraphDocs.addAll(splitByParagraphBlocks(document));
            }

            List<Document> finalChunks = new ArrayList<>();
            int chunkIndex = 0;

            for (Document document : paragraphDocs) {
                List<Document> splitDocs = tokenTextSplitter.split(document);

                for (Document splitDoc : splitDocs) {
                    Map<String, Object> metadata = new HashMap<>(splitDoc.getMetadata());
                    metadata.put("chunkIndex", chunkIndex++);
                    metadata.put("sourceFile", originalFilename);

                    finalChunks.add(new Document(splitDoc.getText(), metadata));
                }
            }

            vectorStore.add(finalChunks);

            return "File ingested successfully: " + originalFilename +
                    " | chunks stored: " + finalChunks.size();

        } catch (Exception e) {
            throw new ApiFallbackException("Failed to ingest file: " + file.getOriginalFilename());
        }
    }

    private Map<String, Object> enrichMetadata(Map<String, Object> metadata, int sectionIndex) {
        Map<String, Object> updated = new HashMap<>(metadata);
        updated.put("sectionIndex", sectionIndex);
        return updated;
    }

    private List<Document> splitByParagraphBlocks(Document sourceDoc) {
        List<Document> docs = new ArrayList<>();

        String[] paragraphs = sourceDoc.getText().split("\\n\\s*\\n");

        StringBuilder current = new StringBuilder();
        int sectionIndex = 0;

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isBlank()) continue;

            // Rough paragraph-group limit before token splitting
            if (current.length() + trimmed.length() > 1200) {
                docs.add(new Document(
                        current.toString().trim(),
                        enrichMetadata(sourceDoc.getMetadata(), sectionIndex++)
                ));
                current.setLength(0);
            }

            current.append(trimmed).append("\n\n");
        }

        if (!current.isEmpty()) {
            docs.add(new Document(
                    current.toString().trim(),
                    enrichMetadata(sourceDoc.getMetadata(), sectionIndex)
            ));
        }

        return docs;
    }

    private String cleanText(String raw) {
        if (raw == null) return "";

        return raw
                .replaceAll("\\r", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .replaceAll("(?m)^\\s+$", "")
                .trim();
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> existingMetadata, String filename) {
        Map<String, Object> metadata = new java.util.HashMap<>(existingMetadata);
        metadata.put("source", filename);
        return metadata;
    }
}
