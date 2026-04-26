package com.onerag.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 向量分块对象。
 * 表示文档分块文本及其 embedding，供写入 Milvus 使用。
 */
@Data
@Builder
@AllArgsConstructor
public class VectorChunk {
    /** 分块序号。 */
    private int index;
    /** 分块文本。 */
    private String content;
    /** 分块唯一标识。 */
    private String chunkId;
    /** 分块向量。 */
    private float[] embedding;
}
