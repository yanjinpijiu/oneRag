package com.onerag.document.dto;

import lombok.Data;

@Data
public class KbDocumentQueryReq {
    private Long pageNo = 1L;
    private Long pageSize = 10L;
    private String status;
    private String keyword;
}
