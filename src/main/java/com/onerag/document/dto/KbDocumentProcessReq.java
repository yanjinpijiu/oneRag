package com.onerag.document.dto;

import lombok.Data;

@Data
public class KbDocumentProcessReq {
    private String strategy = "fixed-size";
    private Integer chunkSize = 500;
    private Integer overlapSize = 50;
}
