package com.an.llm.connector.gateway.repository;

import com.an.llm.connector.gateway.entity.SystemConsumptionStatsEntity;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SystemConsumptionStatsRepo extends JpaRepository<@NonNull SystemConsumptionStatsEntity,@NonNull Long> {
    List<SystemConsumptionStatsEntity> findByCreatedAtBetweenOrderByIdDesc(Instant start, Instant end);

    @Query(value = """
    SELECT *
    FROM system_consumption_stats_entity
    WHERE
    (NULLIF(:agentName, '') IS NULL OR agent_name = :agentName)
    AND (NULLIF(:modelName, '') IS NULL OR model_name = :modelName)
    AND (NULLIF(:server, '') IS NULL OR server = :server)
    AND (CAST(:startDate AS timestamptz) IS NULL OR created_at >= :startDate)
    AND (CAST(:endDate AS timestamptz) IS NULL OR created_at <= :endDate)
    ORDER BY id DESC
    """, nativeQuery = true)
    List<SystemConsumptionStatsEntity> filter(
            @Param("agentName") String agentName,
            @Param("modelName") String modelName,
            @Param("server") String server,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );
}
