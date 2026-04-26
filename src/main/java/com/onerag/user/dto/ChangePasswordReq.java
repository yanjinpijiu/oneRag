package com.onerag.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordReq {
    @NotBlank(message = "oldPassword不能为空")
    private String oldPassword;
    @NotBlank(message = "newPassword不能为空")
    private String newPassword;
}
