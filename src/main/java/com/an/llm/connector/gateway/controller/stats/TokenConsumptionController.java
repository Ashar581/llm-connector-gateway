package com.an.llm.connector.gateway.controller.stats;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.model.filter.TokenStatsFilterRequest;
import com.an.llm.connector.gateway.model.filter.TokenStatsFilterResponse;
import com.an.llm.connector.gateway.service.stats.SystemConsumptionStatsSvc;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/v1/stats")
public class TokenConsumptionController extends BaseApiDelegate {
    private final SystemConsumptionStatsSvc systemConsumptionStatsSvc;

    @GetMapping("current-day")
    public ResponseEntity<@NonNull ApiResponseBody<TokenStatsFilterResponse>> getStatsForCurrentDay(){
        return sendSuccessfulApiResponse(systemConsumptionStatsSvc.getStatsForTheDay(),"Token consumption for the day including all the requests.");
    }

    @GetMapping("filter")
    public ResponseEntity<@NonNull ApiResponseBody<TokenStatsFilterResponse>> getFilteredStatsForCurrentDay(@ModelAttribute TokenStatsFilterRequest filter){
        return sendSuccessfulApiResponse(systemConsumptionStatsSvc.filter(filter),"Token consumption filtered fetched successfully.");
    }

}
