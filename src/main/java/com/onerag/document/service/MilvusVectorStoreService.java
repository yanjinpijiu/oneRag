package com.onerag.document.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.onerag.document.dto.VectorChunk;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.InsertResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Milvus 向量写入服务。
 * 负责把分块文本及其向量编码成 Milvus 行数据并批量写入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusVectorStoreService {

    private final MilvusClientV2 milvusClient;

    @Value("${milvus.collection.name:documents}")
    private String collectionName;

    /**
     * 写入某文档的全部分块向量。
     */
    public void indexDocumentChunks(String docId, String docTitle, List<VectorChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("文档分块为空，跳过向量化存储");
            return;
        }

        List<JsonObject> rows = new ArrayList<>(chunks.size());
        String title = docTitle != null ? docTitle : "";

        for (int i = 0; i < chunks.size(); i++) {
            VectorChunk chunk = chunks.get(i);

            String content = chunk.getContent() == null ? "" : chunk.getContent();
            if (content.length() > 65535) {
                content = content.substring(0, 65535);
            }

            JsonObject metadata = new JsonObject();
            metadata.addProperty("doc_id", docId);
            metadata.addProperty("chunk_index", chunk.getIndex());
            metadata.addProperty("doc_title", title);

            JsonObject row = new JsonObject();
            row.addProperty("doc_id", chunk.getChunkId());
            row.addProperty("content", content);
            row.add("metadata", metadata);
            row.add("embedding", toJsonArray(chunk.getEmbedding()));

            rows.add(row);
        }

        InsertReq req = InsertReq.builder()
                .collectionName(collectionName)
                .data(rows)
                .build();

        InsertResp resp = milvusClient.insert(req);
        log.info("Milvus chunk 建立/写入向量索引成功, collection={}, rows={}", collectionName, resp.getInsertCnt());
    }

    public long deleteDocumentChunks(String docId) {
        String escaped = docId.replace("\"", "\\\"");
        DeleteReq req = DeleteReq.builder()
                .collectionName(collectionName)
                .filter("metadata[\"doc_id\"] == \"" + escaped + "\"")
                .build();
        DeleteResp resp = milvusClient.delete(req);
        long count = resp.getDeleteCnt();
        log.info("Milvus 删除文档向量完成, docId={}, deleteCnt={}", docId, count);
        return count;
    }

    /**
     * 向量数组转 JsonArray。
     */
    private JsonArray toJsonArray(float[] v) {
        JsonArray arr = new JsonArray(v.length);
        for (float x : v) {
            arr.add(x);
        }
        return arr;
    }
}
