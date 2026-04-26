package com.onerag.user.dto;

import lombok.Data;

@Data
public class ProfileUpdateReq {
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
}
