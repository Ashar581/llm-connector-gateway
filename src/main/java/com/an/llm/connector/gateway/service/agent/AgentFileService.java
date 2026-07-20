package com.an.llm.connector.gateway.service.agent;

import com.an.llm.connector.gateway.dto.agent.AgentFileDto;
import com.an.llm.connector.gateway.entity.AgentConfigurationEntity;
import com.an.llm.connector.gateway.entity.AgentFileEntity;
import com.an.llm.connector.gateway.exception.NotFoundException;
import com.an.llm.connector.gateway.mapper.AgentFileMapper;
import com.an.llm.connector.gateway.repository.AgentConfigurationRepository;
import com.an.llm.connector.gateway.repository.AgentFileRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional
    public List<AgentFileDto> add(String agentName, List<MultipartFile> files) {

        AgentConfigurationEntity agent = agentConfigurationRepository.findByName(agentName)
                .orElseThrow(() -> new NotFoundException("Agent does not exists."));

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
            entities.add(entity);
            /**
              since file is added we need to add RAG based functionality too.
             */
        }

        return agentFileMapper.toDtoList(agentFileRepository.saveAll(entities));
    }

    @Transactional
    public Long delete(@NonNull Long id){
        if (!agentFileRepository.existsById(id)) {
            throw new NotFoundException("File does not exist.");
        }

        agentFileRepository.deleteById(id);

        /**
         * Since the file is deleted make sure to deleted things from Vector DB too so that there are no ambiguity.
         */
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
