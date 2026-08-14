package com.an.llm.connector.gateway.mapper.system;

import com.an.llm.connector.gateway.dto.agent.SystemConsumptionStatsDto;
import com.an.llm.connector.gateway.entity.system.SystemConsumptionStatsEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SystemConsumptionStatsMapper {
    @Mappings({
            @Mapping(target = "id",ignore = true),
            @Mapping(target = "source", expression = "java(entity.getSource() != null ? entity.getSource().getValue() : null)"),
            @Mapping(target = "type", expression = "java(entity.getType() != null ? entity.getType().getValue() : null)"),
            @Mapping(target = "modelName", expression = "java(entity.getModelName() != null ? entity.getModelName().getValue() : null)")
    })
    SystemConsumptionStatsDto toDto(SystemConsumptionStatsEntity entity);
    List<SystemConsumptionStatsDto> toDtoList(List<SystemConsumptionStatsEntity> entities);
}
