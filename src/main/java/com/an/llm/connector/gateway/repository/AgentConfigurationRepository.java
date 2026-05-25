package com.an.llm.connector.gateway.repository;

import com.an.llm.connector.gateway.entity.AgentConfigurationEntity;
import com.an.llm.connector.gateway.repository.views.AgentConfigWithFilesView;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentConfigurationRepository extends JpaRepository<@NonNull AgentConfigurationEntity, @NonNull Long> {
    @Query("SELECT a FROM AgentConfigurationEntity a where a.name = :name")
    Optional<AgentConfigurationEntity> findByName(@NonNull @Param("name") String name);
    boolean existsByName(@NonNull String name);

    @Query("""
    SELECT a.id as agentId,
           a.name as agentName,
           a.source as source,
           a.type as type,
           a.model as model,
           a.instructions as instructions,
           a.uniqueId as uniqueId,
           a.active as active,
           a.temperature as temperature,
           a.maxTokens as maxTokens,
           a.isPrivate as isPrivate,
           a.createdBy as createdBy,
           a.createdAt as createdAt,
           a.updatedBy as updatedBy,
           a.updatedAt as updatedAt,
           f.id as fileId,
           f.fileName as fileName,
           f.contentType as contentType,
           f.metadata as metadata
    FROM AgentConfigurationEntity a
    LEFT JOIN AgentFileEntity f
        ON f.agentConfiguration = a
    """)
    List<AgentConfigWithFilesView> fetchAllAgentsWithFiles();
}
