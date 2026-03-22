package com.onerag.document.config;

import com.onerag.document.config.RustFsProperties;
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
                properties.getSecretAccessKey());

        // 构建 S3Client
        S3ClientBuilder builder = S3Client.builder()
                .endpointOverride(URI.create(properties.getUrl()))
                .region(Region.US_EAST_1) // RustFS 不校验 region，可写死
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(true); // 关键配置！RustFS 需启用 Path-Style 访问方式

        S3Client client = builder.build();

        // 测试连接
        try {
            client.listBuckets();
            System.out.println("S3Client连接测试成功");
        } catch (Exception e) {
            System.out.println("S3Client连接测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        return client;
    }
}
