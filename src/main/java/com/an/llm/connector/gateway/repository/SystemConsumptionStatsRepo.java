package com.an.llm.connector.gateway.repository;

import com.an.llm.connector.gateway.dto.SystemConsumptionStatsDto;
import com.an.llm.connector.gateway.entity.SystemConsumptionStatsEntity;
import com.an.llm.connector.gateway.enums.LlmModels;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;

public interface SystemConsumptionStatsRepo extends JpaRepository<@NonNull SystemConsumptionStatsEntity,@NonNull Long> , JpaSpecificationExecutor<@NonNull SystemConsumptionStatsEntity> {
    List<SystemConsumptionStatsEntity> findByCreatedAtBetween(Instant start, Instant end);
}
