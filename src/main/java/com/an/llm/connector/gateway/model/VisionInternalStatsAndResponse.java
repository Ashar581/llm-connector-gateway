package com.an.llm.connector.gateway.model;

import com.an.llm.connector.gateway.entity.system.SystemConsumptionStatsEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//Only for internal usage not an exposable return for any Controller.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VisionInternalStatsAndResponse {
    private String response;
    private SystemConsumptionStatsEntity stats;
}
