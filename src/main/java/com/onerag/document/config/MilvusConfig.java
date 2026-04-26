package com.onerag.document.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 客户端配置。
 */
@Slf4j
@Configuration
public class MilvusConfig {

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.username:}")
    private String username;

    @Value("${milvus.password:}")
    private String password;

    /**
     * 初始化 Milvus Java 客户端。
     */
    @Bean
    public MilvusClientV2 milvusClient() {
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri("http://" + host + ":" + port);

        if (username != null && !username.isEmpty()) {
            builder.token(username + ":" + password);
        }

        MilvusClientV2 client = new MilvusClientV2(builder.build());

        log.info("Milvus客户端初始化成功: {}:{}", host, port);
        return client;
    }
}
