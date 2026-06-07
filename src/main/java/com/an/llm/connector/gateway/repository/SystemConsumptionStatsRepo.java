package com.an.llm.connector.gateway.repository;

import com.an.llm.connector.gateway.entity.SystemConsumptionStatsEntity;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConsumptionStatsRepo extends JpaRepository<@NonNull SystemConsumptionStatsEntity,@NonNull Long> {
}
