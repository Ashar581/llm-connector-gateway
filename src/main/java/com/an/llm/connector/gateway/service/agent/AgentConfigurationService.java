package com.an.llm.connector.gateway.service.agent;

import com.an.llm.connector.gateway.dto.agent.AgentConfigurationDto;
import com.an.llm.connector.gateway.dto.agent.AgentFileDto;
import com.an.llm.connector.gateway.entity.AgentConfigurationEntity;
import com.an.llm.connector.gateway.entity.AgentFileEntity;
import com.an.llm.connector.gateway.enums.*;
import com.an.llm.connector.gateway.exception.*;
import com.an.llm.connector.gateway.mapper.AgentConfigurationMapper;
import com.an.llm.connector.gateway.model.classification.DocumentTypeDefinition;
import com.an.llm.connector.gateway.model.config.ModelConfig;
import com.an.llm.connector.gateway.model.config.SourceConfig;
import com.an.llm.connector.gateway.repository.AgentConfigurationRepository;
import com.an.llm.connector.gateway.repository.views.AgentConfigWithFilesView;
import com.an.llm.connector.gateway.repository.views.AgentFileMetadataView;
import com.an.llm.connector.gateway.repository.AgentFileRepository;
import com.an.llm.connector.gateway.service.LlmConfigService;
import com.an.llm.connector.gateway.service.ai.DocumentIngestionServiceV2;
import com.an.llm.connector.gateway.service.factory.VectorStoreBeanFactory;
import com.an.llm.connector.gateway.util.FileHashGenerator;
import com.an.llm.connector.gateway.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConfigurationService {
    private final LlmConfigService llmConfigService;
    private final AgentConfigurationMapper agentConfigurationMapper;
    private final AgentConfigurationRepository agentConfigurationRepository;
    private final AgentFileRepository agentFileRepository;
    private final DocumentIngestionServiceV2 documentIngestionServiceV2;
    private final VectorStoreBeanFactory vectorStoreBeanFactory;

    public AgentConfigurationDto add(AgentConfigurationDto dto, List<MultipartFile> files, String documentTypeDefinitions){
        if (dto.getName()!=null && !dto.getName().isBlank()) {
            if (agentConfigurationRepository.existsByName(dto.getName())){
                throw new AlreadyExistsException("Agent already exists.");
            }
        }

        verifyLlmAccessibility(dto.getSource(), dto.getModel(), dto.getType(), dto.getMaxTokens());

        //convert and populate the document type definitions if provided.
        if (documentTypeDefinitions != null && !documentTypeDefinitions.isBlank()) {
            List<DocumentTypeDefinition> documentTypes = JsonUtils.deserializeString(documentTypeDefinitions, new TypeReference<List<DocumentTypeDefinition>>() {});

            if (documentTypes == null) throw new NullException("Invalid document type format detected.");
            dto.setDocumentTypes(documentTypes);
        }

        //if type is classification -> expect the mode and documentTypes JSON
        verifyAdditionalConfigurations(dto.getClassificationMode(), dto.getType());

        //if non-classification then make sure to populate the classification related data as null
        if ((documentTypeDefinitions != null || dto.getClassificationMode() != null) && !LlmCapability.CLASSIFICATION.getValue().equalsIgnoreCase(dto.getType())) {
            dto.setDocumentTypes(null);
            dto.setClassificationMode(null);
        }

        //page chunk can be max of 4
        if (dto.getPageChunk() != null && (dto.getPageChunk()>4 || dto.getPageChunk()<1)) throw new NotAllowedException("Page chunk can only be a between 1 to 4.");

        AgentConfigurationEntity entity = agentConfigurationMapper.toEntity(dto);

        //RAG configurations validation
        VectorStore vectorStore = null;
        if (dto.getType().equalsIgnoreCase(LlmCapability.RAG.getValue())) {
            validateRagConfigurations(dto,files);
            vectorStore = vectorStoreBeanFactory.getVectorStore(dto.getVectorStore());
        }

        if (files != null && !files.isEmpty()) {
            if (!dto.getType().equalsIgnoreCase(LlmCapability.RAG.getValue())) {
                throw new NotAllowedException("Only RAG based configurations allows files ingestion.");
            }
            List<AgentFileEntity> agentFileEntities = new ArrayList<>();
            for (MultipartFile file : files) {
                AgentFileEntity agentFileEntity = new AgentFileEntity();
                agentFileEntity.setFileName(file.getOriginalFilename());
                agentFileEntity.setContentType(file.getContentType());
                try {
                agentFileEntity.setData(file.getBytes());
                } catch (Exception e){
                    log.error("Error getting file bytes.",e);
                }
                agentFileEntity.setMetadata(populateMetadata(file));
                try {
                    agentFileEntity.setHashKey(FileHashGenerator.generateSHA256(file));
                } catch (Exception e){
                    log.error("Error while generating hash of file {}",agentFileEntity.getFileName());
                    throw new OperationFailedException("Unable to generate the hash of the attached file.");
                }
                agentFileEntity.setAgentConfiguration(entity);

                agentFileEntities.add(agentFileEntity);
            }
            entity.setFiles(agentFileEntities);
        }

        AgentConfigurationEntity savedAgent = agentConfigurationRepository.save(entity);

        //if rag based then ingest the file as well
        if (dto.getType().equalsIgnoreCase(LlmCapability.RAG.getValue())) {
            if (vectorStore == null) throw new NotFoundException("Invalid vector store detected.");
            if (files!=null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    documentIngestionServiceV2.ingest(
                            IngestionMode.AGENT,
                            file,
                            vectorStore,
                            tokenTextSplitterBuilder(dto),
                            entity.getName()
                    );
                }
            }
        }

        return agentConfigurationMapper.toDto(savedAgent);
    }

    @Transactional(readOnly = true)
    public AgentConfigurationDto get(@NonNull String name){
        if (name.isBlank()) throw new NullException("Agent name is mandatory.");
        AgentConfigurationEntity entity = agentConfigurationRepository.findByName(name)
                .orElseThrow(()-> new NotFoundException("No agent found with the given name."));

        List<AgentFileMetadataView> metadataView = agentFileRepository.findByAgentConfiguration_Name(name);
        if (metadataView != null && !metadataView.isEmpty()) {
            List<AgentFileEntity> agentFileEntities = new ArrayList<>();
            for (AgentFileMetadataView agentFileMetadataView : metadataView) {
                AgentFileEntity agentFileEntity = new AgentFileEntity();
                agentFileEntity.setId(agentFileMetadataView.getId());
                agentFileEntity.setMetadata(agentFileMetadataView.getMetadata());
                agentFileEntity.setContentType(agentFileMetadataView.getContentType());
                agentFileEntity.setFileName(agentFileMetadataView.getFileName());

                agentFileEntities.add(agentFileEntity);
            }
            entity.setFiles(agentFileEntities);
        }

        return agentConfigurationMapper.toDto(entity);
    }

    public List<AgentConfigurationDto> all(){
        List<AgentConfigWithFilesView> rows = agentConfigurationRepository.fetchAllAgentsWithFiles();

        Map<Long, AgentConfigurationDto> map = new LinkedHashMap<>();

        for (AgentConfigWithFilesView row : rows) {

            AgentConfigurationDto agent =
                    map.computeIfAbsent(row.getAgentId(), id -> {
                        AgentConfigurationDto dto = new AgentConfigurationDto();
                        dto.setId(row.getAgentId());
                        dto.setName(row.getAgentName());
                        dto.setInstructions(row.getInstructions());
                        dto.setDescription(row.getDescription());
                        dto.setActive(row.getActive());
                        dto.setModel(row.getModel().getValue());
                        dto.setType(row.getType().getValue());
                        dto.setSource(row.getSource().getValue());
                        dto.setTemperature(row.getTemperature());
                        dto.setIsPrivate(row.getIsPrivate());
                        dto.setMaxTokens(row.getMaxTokens());
                        dto.setUniqueId(row.getUniqueId());
                        dto.setClassificationMode(row.getClassificationMode());
                        dto.setDocumentTypes(row.getDocumentTypes());
                        dto.setPageChunk(row.getPageChunk());
                        dto.setCreatedAt(row.getCreatedAt());
                        dto.setCreatedBy(row.getCreatedBy());
                        dto.setUpdatedBy(row.getUpdatedBy());
                        dto.setUpdatedAt(row.getUpdatedAt());
                        dto.setFiles(new ArrayList<>());
                        return dto;
                    });

            if (row.getFileId() != null) {
                AgentFileDto file = new AgentFileDto();
                file.setId(row.getFileId());
                file.setFileName(row.getFileName());
                file.setContentType(row.getContentType());
                file.setMetadata(row.getMetadata());

                agent.getFiles().add(file);
            }
        }

        return new ArrayList<>(map.values());
    }

    public AgentConfigurationDto update(@NonNull String name, AgentConfigurationDto updateRequested){
        AgentConfigurationEntity entity = agentConfigurationRepository.findByName(name)
                .orElseThrow(()-> new NotFoundException("No agent with given name exists."));

        if (updateRequested.getInstructions()!=null && !updateRequested.getInstructions().isBlank()) {
            entity.setInstructions(updateRequested.getInstructions());
        }
        if (updateRequested.getModel()!=null && !updateRequested.getModel().isBlank()) {
            entity.setModel(LlmModels.getFromValue(updateRequested.getModel()));
        }
        if (updateRequested.getSource() != null && !updateRequested.getSource().isBlank()) {
            entity.setSource(Source.getFromValue(updateRequested.getSource()));
        }
        if (updateRequested.getType() != null && !updateRequested.getType().isBlank()) {
            entity.setType(LlmCapability.getFromValue(updateRequested.getType()));
        }
        entity.setMaxTokens(updateRequested.getMaxTokens());
        if (updateRequested.getTemperature() != null) {
            entity.setTemperature(updateRequested.getTemperature());
        }
        entity.setMaxTokens(updateRequested.getMaxTokens());
        if (updateRequested.getActive() !=null){
            entity.setActive(updateRequested.getActive());
        }
        if (updateRequested.getIsPrivate() != null){
            entity.setIsPrivate(updateRequested.getIsPrivate());
        }
        if (updateRequested.getDocumentTypes() != null) {
            entity.setDocumentTypes(updateRequested.getDocumentTypes());
        }
        if (updateRequested.getClassificationMode() != null) {
            entity.setClassificationMode(updateRequested.getClassificationMode());
        }
        if (updateRequested.getDescription() !=null && !updateRequested.getDescription().isBlank()) {
            entity.setDescription(updateRequested.getDescription());
        }

        if (updateRequested.getClassificationMode() != null) {
            entity.setClassificationMode(updateRequested.getClassificationMode());
        }

        if (updateRequested.getDocumentTypes() != null && !updateRequested.getDocumentTypes().isEmpty()) {
            entity.setDocumentTypes(updateRequested.getDocumentTypes());
        }

        //simple page chunk verification.
        if (updateRequested.getPageChunk() != null && (updateRequested.getPageChunk()>4 || updateRequested.getPageChunk()<1)) throw new NotAllowedException("Page chunk can only be a between 1 to 4.");

        entity.setPageChunk(updateRequested.getPageChunk());

        verifyLlmAccessibility(entity.getSource().getValue(),entity.getModel().getValue(),entity.getType().getValue(), entity.getMaxTokens());
        verifyAdditionalConfigurations(entity.getClassificationMode(),entity.getType().getValue());

        if ((entity.getDocumentTypes() != null || entity.getClassificationMode() != null) && !LlmCapability.CLASSIFICATION.equals(entity.getType())) {
            entity.setDocumentTypes(null);
            entity.setClassificationMode(null);
        }

        //before saving check if the files are there and the type of agent isn't RAG, restrict
        List<AgentFileMetadataView> metadataView = agentFileRepository.findByAgentConfiguration_Name(name);

        if (metadataView != null && !metadataView.isEmpty() && !entity.getType().equals(LlmCapability.RAG)) {
            throw new NotAllowedException("Only RAG based configurations allows files ingestion.");
        }

        entity = agentConfigurationRepository.save(entity);

        //get the files for the final response.
        if (metadataView != null && !metadataView.isEmpty()) {
            List<AgentFileEntity> agentFileEntities = new ArrayList<>();
            for (AgentFileMetadataView agentFileMetadataView : metadataView) {
                AgentFileEntity agentFileEntity = new AgentFileEntity();
                agentFileEntity.setId(agentFileMetadataView.getId());
                agentFileEntity.setMetadata(agentFileMetadataView.getMetadata());
                agentFileEntity.setContentType(agentFileMetadataView.getContentType());
                agentFileEntity.setFileName(agentFileMetadataView.getFileName());

                agentFileEntities.add(agentFileEntity);
            }
            entity.setFiles(agentFileEntities);
        }

        return agentConfigurationMapper.toDto(entity);
    }

    @Transactional
    public String delete(@NonNull String name){
        AgentConfigurationEntity entity = agentConfigurationRepository.findByName(name)
                .orElseThrow(()-> new NotFoundException("Agent does not exist."));

        agentConfigurationRepository.deleteById(entity.getId());
        return name;
    }

    private Map<String,Object> populateMetadata(MultipartFile file){
        Map<String,Object> metadata = new HashMap<>();
        try {
            metadata.put("originalFilename", file.getOriginalFilename());
            metadata.put("contentType", file.getContentType());
            metadata.put("size", file.getSize());
            metadata.put("formFieldName", file.getName());
            metadata.put("empty", file.isEmpty());
        } catch (Exception e){
            log.error("Error populating metadata for the agent file.");
        }

        return metadata;
    }

    private void verifyLlmAccessibility(@NonNull String source, @NonNull String model, @NonNull String type, Integer maxTokens){
        //check if source is valid or not
        Source.getFromValue(source);
        //check if given model is valid or not
        LlmModels.getFromValue(model);
        //check if the given type is valid or not
        LlmCapability.getFromValue(type);

        SourceConfig config = llmConfigService.getModelConfigBySource(Source.getFromValue(source));

        if (config.getModels() == null || config.getModels().isEmpty()) {
            throw new NullException("No model available.");
        }

        boolean modelExists = false;
        //check if the capability is available for the LLM.
        for (ModelConfig modelConfig : config.getModels()) {
            if (model.equalsIgnoreCase(modelConfig.getId())) {
                modelExists = true;
                if (!modelConfig.getType().contains(type)) {
                    throw new NotFoundException(String.format("%s does not have %s capabilities.",model,type));
                }
                int availableMaxTokens = calculateContextPerParallel(modelConfig.getContext(), modelConfig.getParallelExecution());
                if (maxTokens != null) {
                    if (maxTokens>availableMaxTokens) throw new NotAllowedException("Max tokens cannot be greater than "+availableMaxTokens);
                }
            }
        }

        if (!modelExists) throw new NotFoundException("Either the model does not exists or its not under "+source);
    }

    private int calculateContextPerParallel(int totalContext, int parallel) {
        int alignment = 256;
        int raw = totalContext / parallel;
        return  (raw / alignment) * alignment;
    }

    private void verifyAdditionalConfigurations(ClassificationMode classificationMode, String type){
        if (LlmCapability.CLASSIFICATION.getValue().equalsIgnoreCase(type)) {
            //make sure to have the classificationMode populated.
            if (classificationMode == null) throw new NotAllowedException("Document classification mode is mandatory.");
        }
    }

    private void validateRagConfigurations(AgentConfigurationDto dto, List<MultipartFile> files) {
        if (dto.getVectorStore() == null || dto.getVectorStore().isBlank()) throw new NullException("Selecting a vector storage is mandatory.");
        if (dto.getEnablePrivateMode() == null) throw new NullException("Selecting a RAG mode is mandatory.");
        if (dto.getEnablePrivateMode() && (files == null || files.isEmpty())) throw new NotAllowedException("Attaching a knowledge base with private mode enabled is mandatory.");
        if (dto.getChunkSize() != null && dto.getChunkSize() <= 0) throw new NotAllowedException("Chunk size cannot be less than 1.");
        if (dto.getMinChunkLengthToEmbed() != null && dto.getMinChunkLengthToEmbed() <= 0) throw new NotAllowedException("Minimum chunking length to embed cannot be less than 1.");
        if (dto.getMinChunkSizeChars() !=null && dto.getMinChunkSizeChars() <= 0) throw new NotAllowedException("Minimum character chunk size cannot be less than 1.");
        if (dto.getMaxNumChunks() != null && dto.getMaxNumChunks() <= 0) throw new NotAllowedException("Maximum number chunks cannot be less than 1.");

        //also verify the Embedding Model for vector store because vector store is attached with embedding model.
        LlmModels.getFromValue(dto.getVectorStore());
    }

    private TokenTextSplitter tokenTextSplitterBuilder(AgentConfigurationDto dto) {
        TokenTextSplitter.Builder splitter = TokenTextSplitter.builder();

        if (dto.getEncodingType() !=null && !dto.getEncodingType().isBlank()) {
            splitter.withEncodingType(EncodingType.fromName(dto.getEncodingType()).orElseThrow(()-> new OperationFailedException("Invalid encoding type.")));
        } else {
            splitter.withEncodingType(EncodingType.CL100K_BASE);
        }
        if (dto.getChunkSize() != null) {
            splitter.withChunkSize(dto.getChunkSize());
        } else {
            splitter.withChunkSize(200);
        }
        if (dto.getMinChunkLengthToEmbed() != null) {
            splitter.withMinChunkLengthToEmbed(dto.getMinChunkLengthToEmbed());
        } else {
            splitter.withMinChunkLengthToEmbed(100);
        }
        if (dto.getMinChunkSizeChars() != null) {
            splitter.withMinChunkSizeChars(dto.getMinChunkSizeChars());
        } else {
            splitter.withMinChunkSizeChars(100);
        }
        if (dto.getMaxNumChunks() != null) {
            splitter.withMaxNumChunks(dto.getMaxNumChunks());
        } else {
            splitter.withMaxNumChunks(100);
        }
        if (dto.getSeparator() != null) {
            splitter.withKeepSeparator(dto.getSeparator());
        }
        return splitter.build();
    }

}
