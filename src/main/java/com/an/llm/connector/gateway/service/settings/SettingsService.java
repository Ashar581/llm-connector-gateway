package com.an.llm.connector.gateway.service.settings;

import com.an.llm.connector.gateway.dto.settings.SettingsDto;
import com.an.llm.connector.gateway.entity.settings.SettingsEntity;
import com.an.llm.connector.gateway.exception.NotFoundException;
import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.mapper.settings.SettingsMapper;
import com.an.llm.connector.gateway.repository.settings.SettingsRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettingsService {
    private final SettingsRepository settingsRepository;

    private final SettingsMapper settingsMapper;

    public List<SettingsDto> all() {
        return settingsMapper.toDtoList(
                settingsRepository.findAll()
        );
    }

    public List<SettingsDto> add(@NonNull List<SettingsDto> dto) {
        if (dto.isEmpty()) throw new NullException("Settings cannot be empty.");

        Set<String> routePaths = dto.stream()
                .map(SettingsDto::getRoutePath)
                .collect(Collectors.toSet());

        List<SettingsEntity> existingSettings = settingsRepository.findAllByRoutePathIn(routePaths);

        Set<String> existingRoutePaths = existingSettings.stream()
                .map(SettingsEntity::getRoutePath)
                .collect(Collectors.toSet());

        List<SettingsEntity> newSettings = settingsMapper.toEntityList(
                dto.stream()
                        .filter(setting -> !existingRoutePaths.contains(setting.getRoutePath()))
                        .toList()
        );

        return settingsMapper.toDtoList(
                settingsRepository.saveAll(newSettings)
        );
    }

    public SettingsDto add(SettingsDto dto) {
        return settingsMapper.toDto(
                settingsRepository.save(
                        settingsMapper.toEntity(dto)
                )
        );
    }

    @Transactional
    public SettingsDto update(@NonNull SettingsDto updateRequest) {
        SettingsEntity toBeUpdated = settingsRepository.findByRoutePath(updateRequest.getRoutePath())
                .orElseThrow(()-> new NotFoundException("No such route path settings found."));

        if (updateRequest.getRoutePath() != null && !updateRequest.getRoutePath().isBlank()) {
            toBeUpdated.setRoutePath(updateRequest.getRoutePath());
        }
        if (updateRequest.getLabel() != null && !updateRequest.getLabel().isBlank()) {
            toBeUpdated.setLabel(updateRequest.getLabel());
        }
        if (updateRequest.getRoles() != null) {
            toBeUpdated.setRoles(updateRequest.getRoles());
        }

        return settingsMapper.toDto(
                settingsRepository.save(toBeUpdated)
        );
    }

    @Transactional
    public SettingsDto delete(@NonNull String routePath) {
        SettingsEntity toBeDeleted = settingsRepository.findByRoutePath(routePath)
                .orElseThrow(()-> new NotFoundException("No route path settings found that can be deleted"));

        settingsRepository.delete(toBeDeleted);

        return settingsMapper.toDto(toBeDeleted);
    }
}
