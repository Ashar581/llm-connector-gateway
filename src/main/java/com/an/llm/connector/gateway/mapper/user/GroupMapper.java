package com.an.llm.connector.gateway.mapper.user;

import com.an.llm.connector.gateway.dto.user.GroupDto;
import com.an.llm.connector.gateway.entity.user.Group;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GroupMapper {
    Group toEntity(GroupDto dto);
    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "roles", ignore = true)
    })
    GroupDto toDto(Group entity);
    List<GroupDto> toDtoList(List<Group> entities);
}
