package com.onerag.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class RetrievedChunk {
    private String content;
    private float score;
    private String docId;
    private int chunkIndex;
    /** 入库时写入的文档标题/文件名，供回答中标注出处 */
    private String docTitle;
}
