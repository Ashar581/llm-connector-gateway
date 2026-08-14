package com.an.llm.connector.gateway.mapper.user;

import com.an.llm.connector.gateway.dto.user.RoleDto;
import com.an.llm.connector.gateway.entity.user.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RoleMapper {
    Role toEntity(RoleDto dto);
    @Mappings({
            @Mapping(target = "id", ignore = true)
    })
    RoleDto toDto(Role entity);
    List<RoleDto> toDtoList(List<Role> entities);
}
