package com.an.llm.connector.gateway.service.ai;

import com.an.llm.connector.gateway.entity.agent.AgentFileEntity;
import com.an.llm.connector.gateway.enums.IngestionMode;
import com.an.llm.connector.gateway.exception.NotFoundException;
import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.exception.OperationFailedException;
import com.an.llm.connector.gateway.repository.AgentFileRepository;
import com.an.llm.connector.gateway.util.FileHashGenerator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionServiceV2 {
    private final RetrievalServiceV2 retrievalServiceV2;
    private final AgentFileRepository agentFileRepository;

    public void ingest(@NonNull IngestionMode mode, MultipartFile file, @NonNull VectorStore vectorStore, @NonNull TokenTextSplitter tokenTextSplitter, String agent) {
        switch (mode) {
            case CHAT -> ingestForChat(file, vectorStore, tokenTextSplitter);
            case AGENT -> ingestForAgentChat(vectorStore, tokenTextSplitter, agent);
            case null, default -> throw new NotFoundException("Invalid ingestion mode selected.");
        }
    }

    private void ingestForChat(@NonNull MultipartFile file, @NonNull VectorStore vectorStore, @NonNull TokenTextSplitter tokenTextSplitter) {
        if (file.isEmpty()) throw new NullException("Invalid file contents found.");
        try {
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "rag-file";

            String hashKey = FileHashGenerator.generateSHA256(file);

            //find if the file is already ingested or not
            if (retrievalServiceV2.documentExists(vectorStore,hashKey)) {
                log.info("Document already existing. Stopped file ingestion.");
                return;
            }

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
                            mergeMetadata(doc.getMetadata(), originalFilename, hashKey)
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
        } catch (Exception e) {
            log.error("Error while ingesting the file for knowledge-base.",e);
            throw new OperationFailedException("Unable to ingest the file.");
        }
    }

    @Transactional
    private void ingestForAgentChat(@NonNull VectorStore vectorStore, @NonNull TokenTextSplitter tokenTextSplitter, @NonNull String agent) {
        //find if the file is already ingested or not
        List<String> hashKeys = agentFileRepository.findAllHashKeyByAgentName(agent);

        List<String> agentsHavingNoDataInVectorStore = new ArrayList<>();

        if (hashKeys != null && !hashKeys.isEmpty()) {
            for (String hashKey : hashKeys) {
                if (retrievalServiceV2.documentExists(vectorStore, hashKey)) {
                    System.out.println("Document exists.");
                    if (!agent.isBlank()) {
                        log.info("Updating the existing document's metadata with the agent-id {}", agent);
                        retrievalServiceV2.addAgentToDocument(vectorStore, hashKey, agent);
                    }
                } else {
                    agentsHavingNoDataInVectorStore.add(hashKey);
                }
            }
        }

        if (agentsHavingNoDataInVectorStore.isEmpty()) {
            log.info("All documents existing. Stopping ingestion for agent {}", agent);
            return;
        }

        List<AgentFileEntity> agentFilesToBeAddedToVectorStore = agentFileRepository.findAllByHashKeyIn(
                hashKeys.stream()
                        .filter(Objects::nonNull)
                        .toList()
        );

        for (AgentFileEntity file : agentFilesToBeAddedToVectorStore) {
            try {
                ByteArrayResource resource = new ByteArrayResource(file.getData()) {
                    @Override
                    public String getFilename() {
                        return file.getFileName();
                    }
                };

                TikaDocumentReader reader = new TikaDocumentReader(resource);
                List<Document> documents = reader.get();

                List<Document> cleanedDocs = documents.stream()
                        .map(doc -> new Document(
                                cleanText(doc.getText()),
                                mergeMetadata(doc.getMetadata(), file.getFileName(), file.getHashKey())
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
                        metadata.put("sourceFile", file.getFileName());

                        finalChunks.add(new Document(splitDoc.getText(), metadata));
                    }
                }

                vectorStore.add(finalChunks);
            } catch (Exception e) {
                log.error("Error while ingesting the file for knowledge-base for file {} and agent {}.",file.getFileName(),agent);
            }
        }
    }

    private List<Document> splitByParagraphBlocks(Document sourceDoc) {
        List<Document> docs = new ArrayList<>();

        assert sourceDoc.getText() != null;
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

    private Map<String, Object> enrichMetadata(Map<String, Object> metadata, int sectionIndex) {
        Map<String, Object> updated = new HashMap<>(metadata);
        updated.put("sectionIndex", sectionIndex);
        return updated;
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

    private Map<String, Object> mergeMetadata(Map<String, Object> existingMetadata, String filename, String hashKey) {
        Map<String, Object> metadata = new java.util.HashMap<>(existingMetadata);
        metadata.put("source", filename);
        metadata.put("hash", hashKey);
        return metadata;
    }
}
