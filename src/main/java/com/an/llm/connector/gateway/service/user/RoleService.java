package com.an.llm.connector.gateway.service.user;

import com.an.llm.connector.gateway.dto.user.RoleDto;
import com.an.llm.connector.gateway.entity.user.Role;
import com.an.llm.connector.gateway.exception.AlreadyExistsException;
import com.an.llm.connector.gateway.exception.NotFoundException;
import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.mapper.user.RoleMapper;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepo roleRepo;

    private final RoleMapper roleMapper;

    public RoleDto get(@NonNull String code) {
        return roleMapper.toDto(
                roleRepo.findByCode(code).orElseThrow(() -> new NotFoundException("Role does not exists."))
        );
    }

    public List<RoleDto> all() {
        return roleMapper.toDtoList(
                roleRepo.findAll()
        );
    }

    public List<RoleDto> findByCodes(Set<String> codes) {
        return roleMapper.toDtoList(
                roleRepo.findByCodeIn(new ArrayList<>(codes))
        );
    }

    public RoleDto add(@NonNull RoleDto dto) {
        String code = AppUtils.lemmatizeAndUpperCase(dto.getName());

        if (roleRepo.existsByCode(code)) throw new AlreadyExistsException("Role already exists.");

        Role toBeSaved = roleMapper.toEntity(dto);
        toBeSaved.setCode(code);

        return roleMapper.toDto(
                roleRepo.save(toBeSaved)
        );
    }

    public List<RoleDto> add(@NonNull List<RoleDto> dtoList) {
        List<Role> rolesToBeSaved = new ArrayList<>();
        for (RoleDto dto : dtoList) {
            String code = AppUtils.lemmatizeAndUpperCase(dto.getName());

            if (roleRepo.existsByCode(code)) {
                log.error("Role {} already exists. Skipped creation.",dto.getName());
                continue;
            }

            Role toBeSaved = roleMapper.toEntity(dto);
            toBeSaved.setCode(code);

            rolesToBeSaved.add(toBeSaved);
        }

        return roleMapper.toDtoList(
                roleRepo.saveAll(rolesToBeSaved)
        );
    }

    @Transactional
    public RoleDto update(@NonNull RoleDto updateRequest) {
        if (updateRequest.getCode()==null || updateRequest.getCode().isBlank()) throw new NullException("Role code is mandatory.");
        Role existingRole = roleRepo.findByCode(updateRequest.getCode())
                .orElseThrow(() -> new NotFoundException("Role does not exists."));

        if (updateRequest.getDescription() != null) {
            existingRole.setDescription(updateRequest.getDescription());
        }

        return roleMapper.toDto(
                roleRepo.save(existingRole)
        );
    }
}
