package com.onerag.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthLoginResp {
    private String token;
    private String userId;
    private String username;
    private String nickname;
    private String role;
}
