package com.onerag.document.service;

import com.onerag.document.dto.RetrievedChunk;

import java.util.List;

/**
 * 检索结果融合服务
 */
public interface RetrievalFusionService {
    
    /**
     * 融合向量检索和BM25检索的结果
     * @param vectorResults 向量检索结果
     * @param bm25Results BM25检索结果
     * @param topK 返回的结果数量
     * @return 融合后的结果
     */
    List<RetrievedChunk> fuseResults(List<RetrievedChunk> vectorResults, List<RetrievedChunk> bm25Results, int topK);
}
