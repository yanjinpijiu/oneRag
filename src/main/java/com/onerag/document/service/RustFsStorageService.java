package com.onerag.document.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * RustFS 文件存储服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RustFsStorageService {

    private final com.onerag.document.config.RustFsProperties properties;
    private final S3Client s3Client;

    /**
     * 上传文件到 RustFS
     *
     * @param inputStream   文件输入流
     * @param filename      文件名
     * @param contentType   内容类型（MIME 类型）
     * @param metadata      元数据
     * @param contentLength 文件内容长度（字节）
     * @return 文件访问 URL
     */
    public String uploadFile(InputStream inputStream, String filename, String contentType, Map<String, String> metadata,
            long contentLength) {
        return uploadFileDetailed(inputStream, filename, contentType, metadata, contentLength).getFileUrl();
    }

    public UploadResult uploadFileDetailed(InputStream inputStream, String filename, String contentType,
            Map<String, String> metadata, long contentLength) {
        try {
            String bucket = properties.getBucket();
            String objectKey = generateObjectKey(filename);

            log.info("开始上传文件到 RustFS: bucket={}, objectKey={}, size={} bytes, contentType={}",
                    bucket, objectKey, contentLength, contentType);
            log.info("RustFS配置: url={}, accessKeyId={}", properties.getUrl(), properties.getAccessKeyId());

            // 确保bucket存在
            ensureBucketExists(bucket);

            // 构建 PutObjectRequest
            PutObjectRequest.Builder putObjBuilder = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType);

            // 暂时不添加自定义元数据，避免403错误
            // if (metadata != null && !metadata.isEmpty()) {
            // putObjBuilder.metadata(metadata);
            // log.info("添加元数据: {}", metadata);
            // }

            // 上传文件
            RequestBody requestBody = RequestBody.fromInputStream(
                    inputStream,
                    contentLength);

            log.info("开始执行putObject操作...");
            s3Client.putObject(putObjBuilder.build(), requestBody);
            log.info("putObject操作完成");

            String fileUrl = buildFileUrl(bucket, objectKey);
            log.info("文件上传成功：{}", fileUrl);
            return new UploadResult(bucket, objectKey, fileUrl);

        } catch (Exception e) {
            log.error("上传文件到 RustFS 失败", e);
            throw new RuntimeException("文件上传失败：" + e.getMessage(), e);
        }
    }

    public byte[] downloadFile(String bucket, String objectKey) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build()).asByteArray();
        } catch (Exception e) {
            throw new RuntimeException("下载文件失败: " + e.getMessage(), e);
        }
    }

    public void deleteFile(String bucket, String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("删除文件失败: " + e.getMessage(), e);
        }
    }

    public String generatePresignedDownloadUrl(String bucket, String objectKey, long expiresInSeconds) {
        software.amazon.awssdk.services.s3.presigner.S3Presigner presigner = software.amazon.awssdk.services.s3.presigner.S3Presigner
                .builder()
                .endpointOverride(java.net.URI.create(properties.getUrl()))
                .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
                        .create(software.amazon.awssdk.auth.credentials.AwsBasicCredentials
                                .create(properties.getAccessKeyId(), properties.getSecretAccessKey())))
                .region(software.amazon.awssdk.regions.Region.of("us-east-1"))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
        try (presigner) {
            software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest presignRequest = software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
                    .builder()
                    .signatureDuration(Duration.ofSeconds(expiresInSeconds))
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(objectKey).build())
                    .build();
            return presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            throw new RuntimeException("生成下载地址失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查文件是否存在
     *
     * @param bucket    存储桶
     * @param objectKey 对象键
     * @return 是否存在
     */
    public boolean doesObjectExist(String bucket, String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("检查文件存在时发生错误", e);
            return false;
        }
    }

    /**
     * 确保bucket存在，如果不存在则创建
     *
     * @param bucketName bucket名称
     */
    private void ensureBucketExists(String bucketName) {
        try {
            log.info("检查bucket是否存在: {}", bucketName);
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            log.info("Bucket已存在: {}", bucketName);
        } catch (NoSuchBucketException e) {
            log.info("Bucket不存在，正在创建: {}", bucketName);
            try {
                s3Client.createBucket(CreateBucketRequest.builder()
                        .bucket(bucketName)
                        .build());
                log.info("成功创建RustFS存储桶，Bucket名称: {}", bucketName);
            } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ex) {
                log.info("Bucket已存在: {}", bucketName);
            }
        } catch (S3Exception e) {
            log.error("检查bucket时发生S3错误: statusCode={}, message={}", e.statusCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("检查bucket状态时发生错误: {}", bucketName, e);
        }
    }

    /**
     * 生成对象键（包含日期目录结构）
     */
    private String generateObjectKey(String filename) {
        String datePrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uniqueFilename = UUID.randomUUID().toString().replace("-", "") + "_" + filename;
        return datePrefix + "/" + uniqueFilename;
    }

    /**
     * 构建文件访问 URL
     */
    private String buildFileUrl(String bucket, String objectKey) {
        return String.format("%s/%s/%s",
                properties.getUrl(),
                bucket,
                objectKey);
    }

    public static class UploadResult {
        private final String bucket;
        private final String objectKey;
        private final String fileUrl;

        public UploadResult(String bucket, String objectKey, String fileUrl) {
            this.bucket = bucket;
            this.objectKey = objectKey;
            this.fileUrl = fileUrl;
        }

        public String getBucket() {
            return bucket;
        }

        public String getObjectKey() {
            return objectKey;
        }

        public String getFileUrl() {
            return fileUrl;
        }
    }
}
