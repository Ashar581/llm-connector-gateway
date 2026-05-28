package com.an.llm.connector.gateway.mapper;

import com.an.llm.connector.gateway.dto.AgentConfigurationDto;
import com.an.llm.connector.gateway.entity.AgentConfigurationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

@Mapper(componentModel = "spring",uses = {AgentFileMapper.class})
public interface AgentConfigurationMapper {
    @Mappings({
            @Mapping(target = "id",ignore = true),
            @Mapping(target = "source", expression = "java(entity.getSource() != null ? entity.getSource().getValue() : null)"),
            @Mapping(target = "type", expression = "java(entity.getType() != null ? entity.getType().getValue() : null)"),
            @Mapping(target = "model", expression = "java(entity.getModel() != null ? entity.getModel().getValue() : null)"),
    })
    AgentConfigurationDto toDto(AgentConfigurationEntity entity);
    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "source", expression = "java(com.an.llm.connector.gateway.enums.Source.getFromValue(dto.getSource()))"),
            @Mapping(target = "type", expression = "java(com.an.llm.connector.gateway.enums.LlmCapability.getFromValue(dto.getType()))"),
            @Mapping(target = "model", expression = "java(com.an.llm.connector.gateway.enums.LlmModels.getFromValue(dto.getModel()))"),
    })
    AgentConfigurationEntity toEntity(AgentConfigurationDto dto);
    List<AgentConfigurationDto> toDtoList(List<AgentConfigurationEntity> entities);
}
