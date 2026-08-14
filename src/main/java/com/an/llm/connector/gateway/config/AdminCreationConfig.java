package com.an.llm.connector.gateway.config;

import com.an.llm.connector.gateway.dto.user.GroupDto;
import com.an.llm.connector.gateway.dto.user.RoleDto;
import com.an.llm.connector.gateway.dto.user.UserDto;
import com.an.llm.connector.gateway.model.config.AdminUserConfig;
import com.an.llm.connector.gateway.service.user.GroupService;
import com.an.llm.connector.gateway.service.user.RoleService;
import com.an.llm.connector.gateway.service.user.UserService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminCreationConfig {
    private final UserService userService;
    private final RoleService roleService;
    private final GroupService groupService;

    private final AdminUserConfig adminUserConfig;

    @PostConstruct
    private void init() {
        List<RoleDto> adminRolesToBeAdded = new ArrayList<>();
        List<GroupDto> adminGroupToBeAdded = new ArrayList<>();

        log.info("Creating admin roles: {}",adminUserConfig.getUser().getRoles());
        try {
            Set<String> adminRoles = adminUserConfig.getUser().getRoles();
            for (String role : adminRoles) {
                RoleDto roleDto = new RoleDto();
                roleDto.setName(role);

                adminRolesToBeAdded.add(roleDto);
            }
            if (!adminRolesToBeAdded.isEmpty()) {
                adminRolesToBeAdded = roleService.add(adminRolesToBeAdded);
            }
        }catch (Exception e) {
            log.info("Roles already existing. {}", e.getMessage());
        }
        log.info("Creating admin group: {}", adminUserConfig.getUser().getGroups());
        try {
            Set<String> adminGroup = adminUserConfig.getUser().getGroups();
            GroupDto groupDto = new GroupDto();
            groupDto.setName(adminGroup.stream().findFirst().orElse(""));
            groupDto.setRoles(adminRolesToBeAdded);

            GroupDto savedGroup = groupService.add(groupDto);

            adminGroupToBeAdded.add(savedGroup);

        }catch (Exception e) {
            log.info("Group already existing. {}",e.getMessage());
        }
        log.info("Creating admin user {}",adminUserConfig.getUser().getUsername());
        try {
            Set<String> roleCodes = new HashSet<>();
            Set<String> groupCodes = new HashSet<>();
            for (RoleDto role : adminRolesToBeAdded) {
                roleCodes.add(role.getCode());
            }

            for (GroupDto group : adminGroupToBeAdded) {
                groupCodes.add(group.getCode());
            }

            adminUserConfig.getUser().setRoles(roleCodes);
            adminUserConfig.getUser().setGroups(groupCodes);

            UserDto adminUser = userService.add(adminUserConfig.getUser());

            log.info("Admin user created: {}, {}, {}",adminUser.getUsername(), adminUser.getRoles(), adminUser.getGroups());

        }catch (Exception e){
            log.info("User already existing. {}",e.getMessage());
        }
    }
}
