package com.onerag.document.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 通用 OkHttpClient 配置。
 * 提供给模型与向量化等外部 HTTP 调用复用。
 */
@Configuration
public class OkHttpConfig {

    /**
     * 全局 HTTP 客户端 Bean。
     */
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofMinutes(5))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
    }
}
