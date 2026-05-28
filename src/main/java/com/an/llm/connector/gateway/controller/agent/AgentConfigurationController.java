package com.an.llm.connector.gateway.controller.agent;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.dto.AgentConfigurationDto;
import com.an.llm.connector.gateway.service.agent.AgentConfigurationService;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("api/llm/v1/agent-config")
@RequiredArgsConstructor
public class AgentConfigurationController extends BaseApiDelegate {
    private final AgentConfigurationService agentConfigurationService;

    @GetMapping("{name}")
    public ResponseEntity<@NonNull ApiResponseBody<AgentConfigurationDto>> get(@PathVariable("name")String name){
        return sendSuccessfulApiResponse(agentConfigurationService.get(name),"Fetched agent successfully.");
    }
    @GetMapping("")
    public ResponseEntity<@NonNull ApiResponseBody<List<AgentConfigurationDto>>> all(){
        return sendSuccessfulApiResponse(agentConfigurationService.all(),"All agents fetched successfully.");
    }
    @PostMapping("")
    public ResponseEntity<@NonNull ApiResponseBody<AgentConfigurationDto>> add(@Valid @ModelAttribute AgentConfigurationDto dto, @RequestParam(value = "agentFiles", required = false)List<MultipartFile> files){
        return sendCreatedApiResponse(agentConfigurationService.add(dto,files),"Agent was created successfully.");
    }
    @PutMapping("{name}")
    public ResponseEntity<@NonNull ApiResponseBody<AgentConfigurationDto>> update(@PathVariable("name")String name, @RequestBody AgentConfigurationDto updateRequested){
        return sendSuccessfulApiResponse(agentConfigurationService.update(name,updateRequested),"Agent configuration updated successfully.");
    }
    @DeleteMapping("{name}")
    public ResponseEntity<@NonNull ApiResponseBody<String>> delete(@PathVariable("name")String name) {
        return sendSuccessfulApiResponse(agentConfigurationService.delete(name),"Agent deleted successfully.");
    }
}
