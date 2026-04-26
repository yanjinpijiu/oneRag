package com.onerag.document.service;

import com.onerag.document.dto.RetrievedChunk;

import java.util.List;

/**
 * BM25关键词检索服务
 */
public interface BM25RetrieverService {
    
    /**
     * 使用BM25算法检索相关文档
     * @param query 查询语句
     * @param topK 返回的文档数量
     * @return 检索到的文档片段列表
     */
    List<RetrievedChunk> retrieve(String query, int topK);
    
    /**
     * 构建BM25索引
     */
    void buildIndex();
}
