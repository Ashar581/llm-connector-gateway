package com.an.llm.connector.gateway.repository.settings;

import com.an.llm.connector.gateway.entity.settings.SettingsEntity;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SettingsRepository extends JpaRepository<@NonNull SettingsEntity,@NonNull Long> {
    Optional<SettingsEntity> findByRoutePath(String routePath);
    List<SettingsEntity> findAllByRoutePathIn(Set<String> routePath);
}
