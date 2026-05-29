package com.an.llm.connector.gateway.service.agent;

import com.an.llm.connector.gateway.dto.AgentConfigurationDto;
import com.an.llm.connector.gateway.dto.AgentFileDto;
import com.an.llm.connector.gateway.entity.AgentConfigurationEntity;
import com.an.llm.connector.gateway.entity.AgentFileEntity;
import com.an.llm.connector.gateway.enums.ClassificationMode;
import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.enums.LlmModels;
import com.an.llm.connector.gateway.enums.Source;
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
import com.an.llm.connector.gateway.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        verifyAdditionalConfigurations(dto.getDocumentTypes(), dto.getClassificationMode(), dto.getType());

        AgentConfigurationEntity entity = agentConfigurationMapper.toEntity(dto);

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

                agentFileEntity.setAgentConfiguration(entity);

                agentFileEntities.add(agentFileEntity);
            }
            entity.setFiles(agentFileEntities);

            /**
              since file is added we need to add RAG based functionality too.
             */
        }

        return agentConfigurationMapper.toDto(agentConfigurationRepository.save(entity));
    }

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

        verifyLlmAccessibility(entity.getSource().getValue(),entity.getModel().getValue(),entity.getType().getValue(), entity.getMaxTokens());
        verifyAdditionalConfigurations(entity.getDocumentTypes(),entity.getClassificationMode(),entity.getType().getValue());

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

    private void verifyAdditionalConfigurations(List<DocumentTypeDefinition> documentTypeDefinitions, ClassificationMode classificationMode, String type){
        if (LlmCapability.CLASSIFICATION.getValue().equalsIgnoreCase(type)) {
            //make sure to have the classificationMode populated.
            if (classificationMode == null) throw new NotAllowedException("Document classification mode is mandatory.");
            //make sure to have the documentTypes added.
        }
        if ((documentTypeDefinitions != null || classificationMode != null) && !LlmCapability.CLASSIFICATION.getValue().equalsIgnoreCase(type)) {
            throw new NotAllowedException("Only classification type can have the mode and document types defined.");
        }
    }

}
