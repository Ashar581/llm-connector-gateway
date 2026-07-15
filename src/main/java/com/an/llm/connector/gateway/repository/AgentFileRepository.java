package com.an.llm.connector.gateway.repository;

import com.an.llm.connector.gateway.entity.AgentFileEntity;
import com.an.llm.connector.gateway.repository.views.AgentFileMetadataView;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AgentFileRepository extends JpaRepository<@NonNull AgentFileEntity,@NonNull Long> {
    @Query("""
    SELECT f.id as id,
           f.fileName as fileName,
           f.contentType as contentType,
           f.metadata as metadata
    FROM AgentFileEntity f
    WHERE f.agentConfiguration.name = :name
    """)
    List<AgentFileMetadataView> findByAgentConfiguration_Name(@NonNull @Param("name") String name);

    @Query("""
    SELECT f.hashKey
    FROM AgentFileEntity f
    WHERE f.agentConfiguration.name = :name
    """)
    List<String> findAllHashKeyByAgentName(@NonNull @Param("name") String name);
    List<AgentFileEntity> findAllByHashKeyIn(List<String> hashKeys);
}
