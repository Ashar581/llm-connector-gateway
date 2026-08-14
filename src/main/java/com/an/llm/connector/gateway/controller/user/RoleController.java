package com.an.llm.connector.gateway.controller.user;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.dto.user.RoleDto;
import com.an.llm.connector.gateway.exception.OperationFailedException;
import com.an.llm.connector.gateway.service.user.RoleService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/v1/roles")
public class RoleController extends BaseApiDelegate {
    private final RoleService roleService;

    @GetMapping("")
    public ResponseEntity<@NonNull ApiResponseBody<RoleDto>> get(@RequestParam("code") String code) {
        return sendCreatedApiResponse(roleService.get(code),"Role fetched successfully.");
    }

    @GetMapping("/all")
    public ResponseEntity<@NonNull ApiResponseBody<List<RoleDto>>> all() {
        return sendSuccessfulApiResponse(roleService.all(), "Fetched all roles successfully.");
    }

    @GetMapping("by-codes")
    public ResponseEntity<@NonNull ApiResponseBody<List<RoleDto>>> allByCodes(@RequestParam("codes") String codes) {
        try {
            return sendSuccessfulApiResponse(
                    roleService.findByCodes(Arrays.stream(codes.split(",")).collect(Collectors.toSet())),
                    "Fetched role by codes"
            );
        } catch (Exception e) {
            throw new OperationFailedException("Invalid code format found. Should only be comma separated.");
        }
    }

    @PostMapping("")
    public ResponseEntity<@NonNull ApiResponseBody<RoleDto>> add(@RequestBody RoleDto dto) {
        return sendCreatedApiResponse(roleService.add(dto), "Role added successfully");
    }

    @PostMapping("/bulk")
    public ResponseEntity<@NonNull ApiResponseBody<List<RoleDto>>> add(@RequestBody List<RoleDto> dtoList) {
        return sendSuccessfulApiResponse(roleService.add(dtoList), "List of roles added successfully.");
    }
    @PutMapping("")
    public ResponseEntity<@NonNull ApiResponseBody<RoleDto>> update(@RequestBody RoleDto dto) {
        return sendSuccessfulApiResponse(roleService.update(dto),"Role updated successfully.");
    }
}
