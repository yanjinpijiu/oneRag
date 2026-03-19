package com.onerag.document.service.chunking;

import com.onerag.document.dto.TextChunk;
import com.onerag.document.config.ChunkingConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定大小分块策略
 * 按固定字符数进行分割，支持重叠和句子边界检测
 */
@Component
public class FixedSizeChunkingStrategy implements ChunkingStrategy {
    
    @Override
    public List<TextChunk> chunk(String text, ChunkingConfig config) {
        List<TextChunk> chunks = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        
        int chunkSize = config.getChunkSize();
        int overlapSize = config.getOverlapSize();
        boolean enableSentenceBoundary = config.isEnableSentenceBoundary();
        
        int start = 0;
        int index = 0;
        
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            
            // 如果不是最后一个分块，尝试在句子边界断开
            if (end < text.length() && enableSentenceBoundary) {
                end = findBestBreakPoint(text, start, end);
            }
            
            TextChunk chunk = new TextChunk();
            chunk.setIndex(index);
            chunk.setContent(text.substring(start, end));
            chunk.setStartPosition(start);
            chunk.setEndPosition(end);
            chunk.setLength(end - start);
            
            chunks.add(chunk);
            
            // 计算下一个分块的起始位置（考虑重叠）
            if (end >= text.length()) {
                break;
            }
            
            start = end - Math.min(overlapSize, end - start);
            index++;
        }
        
        // 合并过小的分块
        if (config.getMinChunkSize() > 0) {
            chunks = mergeSmallChunks(chunks, config.getMinChunkSize());
        }
        
        return chunks;
    }
    
    /**
     * 查找最佳断点位置
     */
    private int findBestBreakPoint(String text, int start, int expectedEnd) {
        int end = expectedEnd;
        
        // 优先查找的边界符号（按优先级排序）
        char[] boundaries = {'\n', '。', '.', '！', '!', '？', '?', ';', ';'};
        
        // 在 expectedEnd 附近查找边界符号
        int searchStart = start + (expectedEnd - start) / 2;
        
        for (int i = expectedEnd - 1; i >= searchStart; i--) {
            for (char boundary : boundaries) {
                if (text.charAt(i) == boundary) {
                    return i + 1;
                }
            }
        }
        
        // 如果没找到，就使用原定的 end
        return end;
    }
    
    /**
     * 合并过小的分块
     */
    private List<TextChunk> mergeSmallChunks(List<TextChunk> chunks, int minSize) {
        if (chunks.size() <= 1) {
            return chunks;
        }
        
        List<TextChunk> merged = new ArrayList<>();
        TextChunk currentChunk = chunks.get(0);
        
        for (int i = 1; i < chunks.size(); i++) {
            TextChunk nextChunk = chunks.get(i);
            
            if (currentChunk.getLength() < minSize) {
                // 合并到当前分块
                currentChunk.setContent(currentChunk.getContent() + nextChunk.getContent());
                currentChunk.setEndPosition(nextChunk.getEndPosition());
                currentChunk.setLength(currentChunk.getContent().length());
            } else {
                merged.add(currentChunk);
                currentChunk = nextChunk;
            }
        }
        
        merged.add(currentChunk);
        return merged;
    }
    
    @Override
    public String getName() {
        return "fixed-size";
    }
}
