package com.onerag.document.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KbDownloadUrlResp {
    private String url;
    private Long expiresInSeconds;
}
