package com.onerag.document.service.chunking;

import com.onerag.document.dto.TextChunk;
import com.onerag.document.config.ChunkingConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 段落分块策略
 * 按自然段落进行分割，保持语义完整性
 */
@Component
public class ParagraphChunkingStrategy implements ChunkingStrategy {
    
    @Override
    public List<TextChunk> chunk(String text, ChunkingConfig config) {
        List<TextChunk> chunks = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        
        // 按换行符分割段落
        String[] paragraphs = text.split("\\n\\s*\\n");
        
        int index = 0;
        int currentPosition = 0;
        StringBuilder currentChunk = new StringBuilder();
        int chunkStart = 0;
        int maxChunkSize = config.getChunkSize();
        
        for (String paragraph : paragraphs) {
            String trimmedParagraph = paragraph.trim();
            if (trimmedParagraph.isEmpty()) {
                currentPosition += paragraph.length();
                continue;
            }
            
            // 如果当前段落加上已有内容超过限制，先保存现有内容
            if (currentChunk.length() + trimmedParagraph.length() > maxChunkSize && currentChunk.length() > 0) {
                TextChunk chunk = createChunk(index++, currentChunk.toString(), chunkStart, currentPosition);
                chunks.add(chunk);
                currentChunk = new StringBuilder();
                chunkStart = currentPosition;
            }
            
            // 添加段落
            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(trimmedParagraph);
            
            currentPosition += paragraph.length();
        }
        
        // 添加最后一个分块
        if (currentChunk.length() > 0) {
            TextChunk chunk = createChunk(index++, currentChunk.toString(), chunkStart, currentPosition);
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    private TextChunk createChunk(int index, String content, int start, int end) {
        TextChunk chunk = new TextChunk();
        chunk.setIndex(index);
        chunk.setContent(content);
        chunk.setStartPosition(start);
        chunk.setEndPosition(end);
        chunk.setLength(content.length());
        return chunk;
    }
    
    @Override
    public String getName() {
        return "paragraph";
    }
}
