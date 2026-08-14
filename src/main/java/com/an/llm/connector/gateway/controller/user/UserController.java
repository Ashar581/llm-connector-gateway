package com.an.llm.connector.gateway.controller.user;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.dto.user.UserDto;
import com.an.llm.connector.gateway.service.user.UserService;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/v1/users")
public class UserController extends BaseApiDelegate {
    private final UserService userService;

    @GetMapping("")
    public ResponseEntity<@NonNull ApiResponseBody<UserDto>> get(@RequestParam("id") String id) {
        return sendSuccessfulApiResponse(userService.view(id),"User fetched successfully.");
    }

    @GetMapping("/all")
    public ResponseEntity<@NonNull ApiResponseBody<List<UserDto>>> all(@RequestParam(value = "active", required = false) Boolean active){
        return sendSuccessfulApiResponse(userService.all(active),"Fetched all users successfully.");
    }

    @PostMapping("add")
    public ResponseEntity<@NonNull ApiResponseBody<UserDto>> add(@RequestBody @Valid UserDto dto){
        return sendCreatedApiResponse(userService.add(dto), "User added service.");
    }

    @PutMapping("update")
    public ResponseEntity<@NonNull ApiResponseBody<UserDto>> update(@RequestBody UserDto dto) {
        return sendSuccessfulApiResponse(userService.update(dto), "User updated successfully.");
    }
}
