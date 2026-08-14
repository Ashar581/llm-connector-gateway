package com.an.llm.connector.gateway.service.user;

import com.an.llm.connector.gateway.dto.user.GroupDto;
import com.an.llm.connector.gateway.dto.user.RoleDto;
import com.an.llm.connector.gateway.entity.user.Group;
import com.an.llm.connector.gateway.entity.user.Role;
import com.an.llm.connector.gateway.exception.AlreadyExistsException;
import com.an.llm.connector.gateway.exception.NotFoundException;
import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.mapper.user.GroupMapper;
import com.an.llm.connector.gateway.repository.user.GroupRepo;
import com.an.llm.connector.gateway.repository.user.RoleRepo;
import com.an.llm.connector.gateway.util.AppUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {
    private final GroupRepo groupRepo;
    private final RoleRepo roleRepo;

    private final GroupMapper groupMapper;

    public GroupDto get(@NonNull String code) {
        return groupMapper.toDto(
                groupRepo.findByCode(code).orElseThrow(()-> new NotFoundException("Group does not exist."))
        );
    }

    public List<GroupDto> get() {
        return groupMapper.toDtoList(
                groupRepo.findAll()
        );
    }

    public List<GroupDto> getByCodes(@NonNull Set<String> codes) {
        return groupMapper.toDtoList(
                groupRepo.findByCodeIn(new ArrayList<>(codes))
        );
    }

    @Transactional
    public GroupDto add(@NonNull GroupDto dto) {
        String code = AppUtils.lemmatizeAndLowercaseCase(dto.getName());

        if (groupRepo.existsByCode(code)) throw new AlreadyExistsException("Group already exist.");

        Group toBeAdded = groupMapper.toEntity(dto);
        toBeAdded.setCode(code);

        //roles mapping.
        if (dto.getRoles() != null && !dto.getRoles().isEmpty()) {
            Set<String> roleCodes = dto.getRoles().stream()
                    .filter(role -> role.getCode() != null && !role.getCode().isBlank())
                    .map(RoleDto::getCode)
                    .collect(Collectors.toSet());

            List<Role> rolesToBeMapped = roleRepo.findByCodeIn(new ArrayList<>(roleCodes))
                    .stream()
                    .filter(role -> role.getGroup() == null)
                    .toList();

            for (Role role : rolesToBeMapped) {
                role.setGroup(toBeAdded);
            }

            toBeAdded.setRoles(rolesToBeMapped);
        }

        return groupMapper.toDto(
                groupRepo.save(toBeAdded)
        );
    }

    @Transactional
    public GroupDto update(@NonNull GroupDto toBeUpdated) {
        if (toBeUpdated.getCode() == null || toBeUpdated.getCode().isBlank()) throw new NullException("Group code is mandatory.");

        Group existingGroup = groupRepo.findByCode(toBeUpdated.getCode())
                .orElseThrow(()->new NotFoundException("Group does not exist."));

        if (toBeUpdated.getDescription() != null) {
            existingGroup.setDescription(toBeUpdated.getDescription());
        }

        //update the roles
        if (toBeUpdated.getRoles()==null || toBeUpdated.getRoles().isEmpty()) {
            if (existingGroup.getRoles() != null) {
                existingGroup.getRoles().clear();
            }
        } else {
            existingGroup.getRoles().clear();

            Set<String> roleCodes = toBeUpdated.getRoles().stream()
                    .filter(role -> role.getCode() != null && !role.getCode().isBlank())
                    .map(RoleDto::getCode)
                    .collect(Collectors.toSet());

            List<Role> rolesToBeMapped = roleRepo.findByCodeIn(new ArrayList<>(roleCodes))
                    .stream()
                    .filter(role -> role.getGroup() == null)
                    .toList();

            for (Role role : rolesToBeMapped) {
                role.setGroup(existingGroup);
            }

            existingGroup.setRoles(rolesToBeMapped);
        }

        return groupMapper.toDto(
                groupRepo.save(existingGroup)
        );
    }
}
