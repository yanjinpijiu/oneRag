package com.onerag.document.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时确保知识库表存在，避免首次运行缺表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseTableInitializer {
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        String ddl = "CREATE TABLE IF NOT EXISTS kb_document (\n" +
                "    id BIGINT NOT NULL COMMENT '主键ID',\n" +
                "    document_id VARCHAR(64) NOT NULL COMMENT '业务文档ID(UUID)',\n" +
                "    file_name VARCHAR(255) NOT NULL COMMENT '文件名',\n" +
                "    file_ext VARCHAR(32) DEFAULT '' COMMENT '文件扩展名',\n" +
                "    mime_type VARCHAR(128) DEFAULT NULL COMMENT 'MIME类型',\n" +
                "    file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',\n" +
                "    s3_bucket VARCHAR(128) NOT NULL COMMENT 'S3桶',\n" +
                "    s3_object_key VARCHAR(512) NOT NULL COMMENT 'S3对象键',\n" +
                "    file_url VARCHAR(1024) DEFAULT NULL COMMENT '文件URL',\n" +
                "    status VARCHAR(32) NOT NULL COMMENT '状态:UNPROCESSED/RUNNING/COMPLETED/FAILED',\n" +
                "    chunk_count INT NOT NULL DEFAULT 0 COMMENT '分片数量',\n" +
                "    vector_count INT NOT NULL DEFAULT 0 COMMENT '向量数量',\n" +
                "    error_message VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',\n" +
                "    created_by VARCHAR(64) NOT NULL COMMENT '创建人',\n" +
                "    updated_by VARCHAR(64) NOT NULL COMMENT '更新人',\n" +
                "    create_time DATETIME NOT NULL COMMENT '创建时间',\n" +
                "    update_time DATETIME NOT NULL COMMENT '更新时间',\n" +
                "    deleted INTEGER DEFAULT 0 COMMENT '逻辑删除',\n" +
                "    PRIMARY KEY (id),\n" +
                "    UNIQUE KEY uk_document_id (document_id),\n" +
                "    KEY idx_status (status),\n" +
                "    KEY idx_update_time (update_time)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文件表';";
        jdbcTemplate.execute(ddl);
        log.info("知识库表初始化检查完成");
    }
}
