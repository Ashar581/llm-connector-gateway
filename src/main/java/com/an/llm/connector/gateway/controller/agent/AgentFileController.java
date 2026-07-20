package com.an.llm.connector.gateway.controller.agent;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.dto.agent.AgentFileDto;
import com.an.llm.connector.gateway.service.agent.AgentFileService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("api/llm/v1/agent-file")
@RequiredArgsConstructor
public class AgentFileController extends BaseApiDelegate {
    private final AgentFileService agentFileService;

    @PostMapping("{name}")
    public ResponseEntity<@NonNull ApiResponseBody<List<AgentFileDto>>> add(@PathVariable("name")String name, @RequestParam("files") List<MultipartFile> files){
        return sendCreatedApiResponse(agentFileService.add(name,files),"File added to agent successfully.");
    }

    @DeleteMapping("{id}")
    public ResponseEntity<@NonNull ApiResponseBody<Long>> delete(@PathVariable("id")Long id){
        return sendSuccessfulApiResponse(agentFileService.delete(id),"File deleted successfully.");
    }
}
