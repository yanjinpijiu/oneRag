package com.onerag.document.service;

import com.onerag.document.dto.RetrievedChunk;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Milvus 向量检索服务。
 * 将查询向量化后执行 ANN 检索，并反序列化为 RetrievedChunk。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusRetrieverService {

    private final MilvusClientV2 milvusClient;
    private final EmbeddingService embeddingService;

    @Value("${milvus.collection.name:documents}")
    private String collectionName;

    private static final int TOP_K = 5;

    /**
     * 检索与 query 最相关的文档片段。
     */
    public List<RetrievedChunk> retrieve(String query) {
        try {
            List<Float> queryVector = embeddingService.embed(query);
            float[] vector = toArray(queryVector);

            List<BaseVector> vectors = List.of(new FloatVec(vector));

            Map<String, Object> params = new HashMap<>();
            params.put("metric_type", "COSINE");
            params.put("ef", 128);

            SearchReq searchReq = SearchReq.builder()
                    .collectionName(collectionName)
                    .annsField("embedding")
                    .data(vectors)
                    .topK(TOP_K)
                    .searchParams(params)
                    .outputFields(List.of("content", "metadata"))
                    .build();

            SearchResp searchResp = milvusClient.search(searchReq);

            List<RetrievedChunk> results = new ArrayList<>();
            List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();

            if (searchResults != null && !searchResults.isEmpty()) {
                for (SearchResp.SearchResult result : searchResults.get(0)) {
                    Map<String, Object> entity = result.getEntity();

                    String content = entity.get("content") != null ? entity.get("content").toString() : "";
                    float score = result.getScore();

                    String docId = "";
                    int chunkIndex = 0;
                    String docTitle = "";

                    if (entity.get("metadata") != null) {
                        Object metadataObj = entity.get("metadata");
                        if (metadataObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> metadata = (Map<String, Object>) metadataObj;
                            if (metadata.containsKey("doc_id")) {
                                docId = metadata.get("doc_id").toString();
                            }
                            if (metadata.containsKey("chunk_index")) {
                                Object chunkIndexObj = metadata.get("chunk_index");
                                if (chunkIndexObj instanceof Number) {
                                    chunkIndex = ((Number) chunkIndexObj).intValue();
                                }
                            }
                            if (metadata.containsKey("doc_title")) {
                                Object t = metadata.get("doc_title");
                                if (t != null) {
                                    docTitle = t.toString();
                                }
                            }
                        }
                    }

                    results.add(RetrievedChunk.builder()
                            .content(content)
                            .score(score)
                            .docId(docId)
                            .chunkIndex(chunkIndex)
                            .docTitle(docTitle)
                            .build());
                }
            }

            log.info("从 Milvus 检索到 {} 个相关文档片段", results.size());
            return results;

        } catch (Exception e) {
            log.error("从 Milvus 检索失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * Float 列表转原生数组。
     */
    private float[] toArray(List<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}
