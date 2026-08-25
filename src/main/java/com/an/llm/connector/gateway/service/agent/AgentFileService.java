package com.an.llm.connector.gateway.service.agent;

import com.an.llm.connector.gateway.dto.agent.AgentFileDto;
import com.an.llm.connector.gateway.entity.agent.AgentConfigurationEntity;
import com.an.llm.connector.gateway.entity.agent.AgentFileEntity;
import com.an.llm.connector.gateway.enums.IngestionMode;
import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.exception.NotAllowedException;
import com.an.llm.connector.gateway.exception.NotFoundException;
import com.an.llm.connector.gateway.exception.OperationFailedException;
import com.an.llm.connector.gateway.mapper.agent.AgentFileMapper;
import com.an.llm.connector.gateway.repository.AgentConfigurationRepository;
import com.an.llm.connector.gateway.repository.AgentFileRepository;
import com.an.llm.connector.gateway.repository.views.AgentFileDeletionView;
import com.an.llm.connector.gateway.service.ai.DocumentIngestionServiceV2;
import com.an.llm.connector.gateway.service.ai.RetrievalServiceV2;
import com.an.llm.connector.gateway.service.factory.VectorStoreBeanFactory;
import com.an.llm.connector.gateway.util.FileHashGenerator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentFileService {
    private final AgentConfigurationRepository agentConfigurationRepository;
    private final AgentFileRepository agentFileRepository;
    private final AgentFileMapper agentFileMapper;

    private final RetrievalServiceV2 retrievalServiceV2;
    private final VectorStoreBeanFactory vectorStoreBeanFactory;

    @Transactional
    public List<AgentFileDto> add(String agentName, List<MultipartFile> files) {

        AgentConfigurationEntity agent = agentConfigurationRepository.findByName(agentName)
                .orElseThrow(() -> new NotFoundException("Agent does not exists."));

        if (!agent.getType().equals(LlmCapability.RAG)) throw new NotAllowedException("Files can only be attached to RAG based agents.");

        List<AgentFileEntity> entities = new ArrayList<>();

        for (MultipartFile file : files) {
            AgentFileEntity entity = new AgentFileEntity();

            entity.setFileName(file.getOriginalFilename());
            entity.setContentType(file.getContentType());
            entity.setMetadata(populateMetadata(file));
            entity.setAgentConfiguration(agent);
            try {
                entity.setData(file.getBytes());
            } catch (Exception e) {
                log.error("Error getting file bytes.", e);
            }

            try {
                entity.setHashKey(FileHashGenerator.generateSHA256(file));
            } catch (Exception e){
                log.error("Error while generating hash of file {}",entity.getFileName());
                throw new OperationFailedException("Unable to generate the hash of the attached file.");
            }
            entities.add(entity);
        }

        return agentFileMapper.toDtoList(agentFileRepository.saveAll(entities));
    }

    @Transactional
    public Long delete(@NonNull Long id){
        AgentFileDeletionView file = agentFileRepository.findDeletionDataById(id)
                .orElseThrow(() -> new NotFoundException("File does not exist."));

        VectorStore vectorStore = vectorStoreBeanFactory.getVectorStore(file.getVectorStore());
        retrievalServiceV2.removeAgentFromDocument(vectorStore,file.getHashKey(), file.getAgentName());
        agentFileRepository.deleteById(id);

        return id;
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
}
