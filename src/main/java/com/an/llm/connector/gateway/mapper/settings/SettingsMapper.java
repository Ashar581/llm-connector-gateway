package com.an.llm.connector.gateway.mapper.settings;

import com.an.llm.connector.gateway.dto.settings.SettingsDto;
import com.an.llm.connector.gateway.entity.settings.SettingsEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SettingsMapper {
    SettingsEntity toEntity(SettingsDto dto);
    List<SettingsEntity> toEntityList(List<SettingsDto> dtoList);
    @Mapping(target = "id",ignore = true)
    SettingsDto toDto(SettingsEntity entity);
    List<SettingsDto> toDtoList(List<SettingsEntity> entities);
}
