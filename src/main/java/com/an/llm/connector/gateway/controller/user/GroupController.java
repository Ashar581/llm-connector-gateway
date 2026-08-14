package com.an.llm.connector.gateway.controller.user;


import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.dto.user.GroupDto;
import com.an.llm.connector.gateway.exception.OperationFailedException;
import com.an.llm.connector.gateway.service.user.GroupService;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/v1/groups")
public class GroupController extends BaseApiDelegate {
    private final GroupService groupService;

    @GetMapping("")
    public ResponseEntity<@NonNull ApiResponseBody<GroupDto>> get(@RequestParam("code")String code) {
        return sendSuccessfulApiResponse(groupService.get(code),"Group fetched successfully.");
    }

    @GetMapping("/all")
    public ResponseEntity<@NonNull ApiResponseBody<List<GroupDto>>> all() {
        return sendSuccessfulApiResponse(groupService.get(),"Fetched all groups successfully.");
    }

    @GetMapping("/by-codes")
    public ResponseEntity<@NonNull ApiResponseBody<List<GroupDto>>> getByCodes(@RequestParam("codes")String codes) {
        try {
            return sendSuccessfulApiResponse(
                    groupService.getByCodes(Arrays.stream(codes.split(",")).collect(Collectors.toSet())),
                    "Fetched the groups by codes."
            );
        } catch (Exception e) {
            throw new OperationFailedException("Invalid code format found. Should be only comma separated.");
        }
    }

    @PostMapping("")
    public ResponseEntity<@NonNull ApiResponseBody<GroupDto>> add(@RequestBody @Valid GroupDto dto) {
        return sendCreatedApiResponse(groupService.add(dto),"Group added successfully");
    }

    @PutMapping("")
    public ResponseEntity<@NonNull ApiResponseBody<GroupDto>> update(@RequestBody GroupDto dto) {
        return sendSuccessfulApiResponse(groupService.update(dto),"Group updated successfully.");
    }
}
