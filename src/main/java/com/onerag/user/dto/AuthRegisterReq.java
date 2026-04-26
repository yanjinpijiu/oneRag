package com.onerag.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthRegisterReq {
    @NotBlank(message = "username不能为空")
    private String username;
    @NotBlank(message = "password不能为空")
    private String password;
    @NotBlank(message = "nickname不能为空")
    private String nickname;
    private String email;
    private String phone;
}
