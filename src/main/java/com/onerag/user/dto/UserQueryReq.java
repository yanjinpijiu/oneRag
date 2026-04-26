package com.onerag.user.dto;

import lombok.Data;

@Data
public class UserQueryReq {
    private long pageNo = 1;
    private long pageSize = 10;
    private String keyword;
    private Integer status;
    private String role;
}
