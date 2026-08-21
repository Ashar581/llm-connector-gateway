package com.an.llm.connector.gateway.controller.settings;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.dto.settings.SettingsDto;
import com.an.llm.connector.gateway.service.settings.SettingsService;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/llm/v1/settings")
public class SettingsController extends BaseApiDelegate {
    private final SettingsService settingsService;

    @GetMapping("/all")
    public ResponseEntity<@NonNull ApiResponseBody<List<SettingsDto>>> all() {
        return sendSuccessfulApiResponse(settingsService.all(),"Fetched all route path settings successfully.");
    }

    @PostMapping("/bulk/add")
    public ResponseEntity<@NonNull ApiResponseBody<List<SettingsDto>>> add(@RequestBody @Valid List<SettingsDto> toBeAdded) {
        return sendCreatedApiResponse(settingsService.add(toBeAdded),"All route path settings created successfully.");
    }

    @PostMapping("/add")
    public ResponseEntity<@NonNull ApiResponseBody<SettingsDto>> add(@RequestBody @Valid SettingsDto toBeAdded) {
        return sendCreatedApiResponse(settingsService.add(toBeAdded),"Route path setting created successfully.");
    }

    @PutMapping("/update")
    public ResponseEntity<@NonNull ApiResponseBody<SettingsDto>> update(@RequestBody SettingsDto toBeUpdated) {
        return sendSuccessfulApiResponse(settingsService.update(toBeUpdated),"Route path setting updated successfully.");
    }

    @DeleteMapping("/delete")
    public ResponseEntity<@NonNull ApiResponseBody<SettingsDto>> delete(@RequestParam("route") String routePath) {
        return sendSuccessfulApiResponse(settingsService.delete(routePath),"Route path deleted successfully.");
    }
}
