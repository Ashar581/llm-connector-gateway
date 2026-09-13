package com.an.llm.connector.gateway.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {
    @NotNull(message = "Userid is mandatory.")
    @NotBlank(message = "Userid is mandatory.")
    private String userid;
    @NotNull(message = "Current password is mandatory.")
    @NotBlank(message = "Current password is mandatory.")
    private String currentPassword;
    @NotNull(message = "New password is mandatory.")
    @NotBlank(message = "New password is mandatory.")
    private String newPassword;
    @NotNull(message = "Confirm password is mandatory.")
    @NotBlank(message = "Confirm password is mandatory.")
    private String confirmPassword;
}
