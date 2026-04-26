package com.onerag.document.service.impl;

import com.onerag.document.dto.RetrievedChunk;
import com.onerag.document.service.RetrievalFusionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RetrievalFusionServiceImpl implements RetrievalFusionService {

    private static final double RRF_K = 60.0; // RRF参数

    @Override
    public List<RetrievedChunk> fuseResults(List<RetrievedChunk> vectorResults, List<RetrievedChunk> bm25Results,
            int topK) {
        // 创建结果映射，用于去重和计算融合得分
        Map<String, FusedResult> resultMap = new HashMap<>();

        // 处理向量检索结果
        for (int i = 0; i < vectorResults.size(); i++) {
            RetrievedChunk chunk = vectorResults.get(i);
            String key = generateChunkKey(chunk);
            double rrfScore = 1.0 / (RRF_K + i + 1);

            FusedResult fusedResult = resultMap.getOrDefault(key, new FusedResult(chunk));
            fusedResult.addVectorScore(chunk.getScore());
            fusedResult.addRrfScore(rrfScore);
            resultMap.put(key, fusedResult);
        }

        // 处理BM25检索结果
        for (int i = 0; i < bm25Results.size(); i++) {
            RetrievedChunk chunk = bm25Results.get(i);
            String key = generateChunkKey(chunk);
            double rrfScore = 1.0 / (RRF_K + i + 1);

            FusedResult fusedResult = resultMap.getOrDefault(key, new FusedResult(chunk));
            fusedResult.addBm25Score(chunk.getScore());
            fusedResult.addRrfScore(rrfScore);
            resultMap.put(key, fusedResult);
        }

        // 计算最终得分并排序
        List<RetrievedChunk> fusedResults = resultMap.values().stream()
                .map(FusedResult::toRetrievedChunk)
                .sorted(Comparator.comparingDouble(RetrievedChunk::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());

        log.info("融合检索结果完成，向量检索: {}个，BM25检索: {}个，融合后: {}个",
                vectorResults.size(), bm25Results.size(), fusedResults.size());

        return fusedResults;
    }

    /**
     * 生成文档片段的唯一键
     */
    private String generateChunkKey(RetrievedChunk chunk) {
        return chunk.getDocId() + "_" + chunk.getChunkIndex();
    }

    /**
     * 融合结果内部类
     */
    private static class FusedResult {
        private final RetrievedChunk chunk;
        private double vectorScore = 0.0;
        private double bm25Score = 0.0;
        private double rrfScore = 0.0;

        public FusedResult(RetrievedChunk chunk) {
            this.chunk = chunk;
        }

        public void addVectorScore(double score) {
            this.vectorScore = score;
        }

        public void addBm25Score(double score) {
            this.bm25Score = score;
        }

        public void addRrfScore(double score) {
            this.rrfScore += score;
        }

        public RetrievedChunk toRetrievedChunk() {
            return RetrievedChunk.builder()
                    .content(chunk.getContent())
                    .score((float) rrfScore)
                    .docId(chunk.getDocId())
                    .chunkIndex(chunk.getChunkIndex())
                    .docTitle(chunk.getDocTitle())
                    .build();
        }
    }
}
