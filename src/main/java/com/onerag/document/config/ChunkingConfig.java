package com.onerag.document.config;

import lombok.Getter;
import lombok.Setter;

/**
 * 分块配置参数
 */
@Setter
@Getter
public class ChunkingConfig {

    /**
     * 分块大小（字符数）
     */
    private int chunkSize = 500;

    /**
     * 重叠大小（字符数）
     */
    private int overlapSize = 50;

    /**
     * 是否启用句子边界检测
     */
    private boolean enableSentenceBoundary = true;

    /**
     * 最小分块大小（小于这个值的分块会合并到前一个分块）
     */
    private int minChunkSize = 100;

    public ChunkingConfig() {
    }

    public ChunkingConfig(int chunkSize, int overlapSize) {
        this.chunkSize = chunkSize;
        this.overlapSize = overlapSize;
    }

    public ChunkingConfig(int chunkSize, int overlapSize, boolean enableSentenceBoundary) {
        this.chunkSize = chunkSize;
        this.overlapSize = overlapSize;
        this.enableSentenceBoundary = enableSentenceBoundary;
    }
}