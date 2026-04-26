package com.onerag.document.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class KbDocumentResp {
    private String documentId;
    private String fileName;
    private String fileExt;
    private String mimeType;
    private Long fileSize;
    private String status;
    private Integer chunkCount;
    private Integer vectorCount;
    private String errorMessage;
    private Date createTime;
    private Date updateTime;
}
