package com.onerag.user.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PageResp<T> {
    private long pageNo;
    private long pageSize;
    private long total;
    private List<T> records;
}
