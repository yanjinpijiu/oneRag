package com.onerag.user.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class UserResp {
    private String userId;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer status;
    private String role;
    private Date lastLoginTime;
    private Date createTime;
    private Date updateTime;
}
