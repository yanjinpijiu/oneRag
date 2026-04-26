package com.onerag.user.dto;

import lombok.Data;

@Data
public class UserUpdateReq {
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer status;
    private String role;
}
