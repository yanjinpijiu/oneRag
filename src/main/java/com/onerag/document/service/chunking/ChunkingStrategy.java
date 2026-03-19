package com.onerag.document.service.chunking;

import com.onerag.document.dto.TextChunk;
import com.onerag.document.config.ChunkingConfig;

import java.util.List;

/**
 * 文本分块策略接口
 */
public interface ChunkingStrategy {
    
    /**
     * 将文本分割成多个分块
     *
     * @param text 待分块的文本
     * @param config 分块配置
     * @return 分块列表
     */
    List<TextChunk> chunk(String text, ChunkingConfig config);
    
    /**
     * 将文本分割成多个分块（使用默认配置）
     *
     * @param text 待分块的文本
     * @return 分块列表
     */
    default List<TextChunk> chunk(String text) {
        return chunk(text, new ChunkingConfig());
    }
    
    /**
     * 获取策略名称
     *
     * @return 策略名称
     */
    String getName();
}
