package com.onerag.document.config;

import com.onerag.document.service.RustFsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * RustFS S3 Client 配置
 */
@Configuration
@RequiredArgsConstructor
public class RustFsConfig {
    
    private final RustFsProperties properties;
    
    /**
     * 配置 S3 Client Bean
     */
    @Bean
    public S3Client s3Client() {
        // 创建凭证
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
            properties.getAccessKeyId(),
            properties.getSecretAccessKey()
        );
        
        // 构建 S3Client
        S3ClientBuilder builder = S3Client.builder()
            .region(Region.of("auto")) // RustFS 通常使用 auto region
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .endpointOverride(URI.create(properties.getUrl()))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true) // 启用路径式访问
                .build());
        
        return builder.build();
    }
}
