package com.an.llm.connector.gateway.mapper.agent;

import com.an.llm.connector.gateway.dto.agent.AgentFileDto;
import com.an.llm.connector.gateway.entity.agent.AgentFileEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AgentFileMapper {
    @Mappings({
            @Mapping(target = "agentConfiguration", ignore = true)
    })
    AgentFileDto toDto(AgentFileEntity entity);
    @Mappings({
            @Mapping(target = "agentConfiguration", ignore = true)
    })
    AgentFileEntity toEntity(AgentFileDto dto);

    List<AgentFileDto> toDtoList(List<AgentFileEntity> entities);
}
