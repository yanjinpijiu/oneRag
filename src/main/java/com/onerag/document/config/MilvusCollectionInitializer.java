package com.onerag.document.config;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 启动时初始化 Milvus Collection。
 * 若集合不存在则自动创建 schema 与 HNSW 索引。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusCollectionInitializer implements CommandLineRunner {

    private final MilvusClientV2 milvusClient;

    @Value("${milvus.collection.name:documents}")
    private String collectionName;

    private static final int EMBEDDING_DIM = 4096;

    /**
     * 应用启动后执行集合检查与创建。
     */
    @Override
    public void run(String... args) {
        boolean exists = Boolean.TRUE.equals(milvusClient.hasCollection(
                HasCollectionReq.builder().collectionName(collectionName).build()
        ));
        
        if (exists) {
            log.info("Milvus collection '{}' 已存在", collectionName);
            return;
        }

        log.info("开始创建 Milvus collection: {}", collectionName);

        List<CreateCollectionReq.FieldSchema> fieldSchemaList = new ArrayList<>();

        fieldSchemaList.add(
                CreateCollectionReq.FieldSchema.builder()
                        .name("doc_id")
                        .dataType(DataType.VarChar)
                        .maxLength(36)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .build()
        );

        fieldSchemaList.add(
                CreateCollectionReq.FieldSchema.builder()
                        .name("content")
                        .dataType(DataType.VarChar)
                        .maxLength(65535)
                        .build()
        );

        fieldSchemaList.add(
                CreateCollectionReq.FieldSchema.builder()
                        .name("metadata")
                        .dataType(DataType.JSON)
                        .build()
        );

        fieldSchemaList.add(
                CreateCollectionReq.FieldSchema.builder()
                        .name("embedding")
                        .dataType(DataType.FloatVector)
                        .dimension(EMBEDDING_DIM)
                        .build()
        );

        CreateCollectionReq.CollectionSchema collectionSchema = CreateCollectionReq.CollectionSchema
                .builder()
                .fieldSchemaList(fieldSchemaList)
                .build();

        IndexParam hnswIndex = IndexParam.builder()
                .fieldName("embedding")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .indexName("embedding")
                .extraParams(Map.of(
                        "M", "48",
                        "efConstruction", "200",
                        "mmap.enabled", "false"
                ))
                .build();

        CreateCollectionReq createReq = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(collectionSchema)
                .primaryFieldName("doc_id")
                .vectorFieldName("embedding")
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .indexParams(List.of(hnswIndex))
                .description("OneRAG 文档向量集合")
                .build();

        milvusClient.createCollection(createReq);
        log.info("成功创建 Milvus collection: {}", collectionName);
    }
}
