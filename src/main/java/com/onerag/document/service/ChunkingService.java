package com.onerag.document.service;

import com.onerag.document.dto.TextChunk;
import com.onerag.document.config.ChunkingConfig;
import com.onerag.document.service.chunking.ChunkingStrategy;
import com.onerag.document.service.chunking.FixedSizeChunkingStrategy;
import com.onerag.document.service.chunking.ParagraphChunkingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本分块服务
 */
@Slf4j
@Service
public class ChunkingService {
    
    private final Map<String, ChunkingStrategy> strategies = new HashMap<>();
    
    @Autowired
    public ChunkingService(
            FixedSizeChunkingStrategy fixedSizeStrategy,
            ParagraphChunkingStrategy paragraphStrategy) {
        strategies.put(fixedSizeStrategy.getName(), fixedSizeStrategy);
        strategies.put(paragraphStrategy.getName(), paragraphStrategy);
        log.info("初始化分块策略：{}", strategies.keySet());
    }
    
    /**
     * 使用指定策略进行分块
     *
     * @param text 待分块的文本
     * @param strategyName 策略名称（fixed-size, paragraph）
     * @param config 分块配置
     * @return 分块列表
     */
    public List<TextChunk> chunk(String text, String strategyName, ChunkingConfig config) {
        ChunkingStrategy strategy = strategies.get(strategyName);
        if (strategy == null) {
            log.warn("未找到分块策略：{}，使用默认策略 fixed-size", strategyName);
            strategy = strategies.get("fixed-size");
        }
        
        log.info("使用分块策略：{}，配置：chunkSize={}, overlap={}", 
            strategyName, config.getChunkSize(), config.getOverlapSize());
        
        List<TextChunk> chunks = strategy.chunk(text, config);
        log.info("分块完成，共分块：{} 个", chunks.size());
        
        return chunks;
    }
    
    /**
     * 使用指定策略进行分块（使用默认配置）
     *
     * @param text 待分块的文本
     * @param strategyName 策略名称
     * @return 分块列表
     */
    public List<TextChunk> chunk(String text, String strategyName) {
        return chunk(text, strategyName, new ChunkingConfig());
    }
    
    /**
     * 使用默认策略（固定大小）进行分块
     *
     * @param text 待分块的文本
     * @param config 分块配置
     * @return 分块列表
     */
    public List<TextChunk> chunk(String text, ChunkingConfig config) {
        return chunk(text, "fixed-size", config);
    }
    
    /**
     * 使用默认策略（固定大小）进行分块（使用默认配置）
     *
     * @param text 待分块的文本
     * @return 分块列表
     */
    public List<TextChunk> chunk(String text) {
        return chunk(text, "fixed-size", new ChunkingConfig());
    }
    
    /**
     * 获取所有可用的分块策略
     *
     * @return 策略名称列表
     */
    public List<String> getAvailableStrategies() {
        return strategies.keySet().stream().toList();
    }
}
