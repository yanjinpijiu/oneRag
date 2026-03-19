package com.onerag.document.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 文本分块
 */
@Setter
@Getter
public class TextChunk {
    
    /**
     * 分块索引（从 0 开始）
     */
    private Integer index;
    
    /**
     * 分块内容
     */
    private String content;
    
    /**
     * 分块起始位置（字符位置）
     */
    private Integer startPosition;
    
    /**
     * 分块结束位置（字符位置）
     */
    private Integer endPosition;
    
    /**
     * 分块长度（字符数）
     */
    private Integer length;
    
    /**
     * 元数据（可选）
     */
    private Object metadata;
}
