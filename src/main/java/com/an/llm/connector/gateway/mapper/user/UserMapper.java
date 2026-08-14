package com.an.llm.connector.gateway.mapper.user;

import com.an.llm.connector.gateway.dto.user.UserDto;
import com.an.llm.connector.gateway.entity.user.Group;
import com.an.llm.connector.gateway.entity.user.Role;
import com.an.llm.connector.gateway.entity.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mappings({
            @Mapping(target = "roles", ignore = true),
            @Mapping(target = "groups", ignore = true)
    })
    User toEntity(UserDto dto);
    @Mappings({
            @Mapping(target = "id",ignore = true),
            @Mapping(target = "groups", source = "groups"),
            @Mapping(target = "roles", source = "roles"),
            @Mapping(target = "password", ignore = true)
    })
    UserDto toDto(User entity);
    List<UserDto> toDtoList(List<User> entities);

    default Set<String> mapGroups(Set<Group> groups) {
        if (groups == null) {
            return Collections.emptySet();
        }

        return groups.stream()
                .map(Group::getCode)
                .collect(Collectors.toSet());
    }

    default Set<String> mapRoles(Set<Role> roles) {
        if (roles == null) {
            return Collections.emptySet();
        }

        return roles.stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());
    }
}
