package com.onerag.document.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.net.URI;
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

            return fileUrl;

        } catch (Exception e) {
            log.error("上传文件到 RustFS 失败", e);
            throw new RuntimeException("文件上传失败：" + e.getMessage(), e);
        }
    }

    // /**
    // * 上传文件到 RustFS（简化版，不需要元数据）
    // *
    // * @param inputStream 文件输入流
    // * @param filename 文件名
    // * @param contentType 内容类型
    // * @return 文件访问 URL
    // */
    // public String uploadFile(InputStream inputStream, String filename, String
    // contentType) {
    // return uploadFile(inputStream, filename, contentType, null);
    // }
    //
    // /**
    // * 删除文件
    // *
    // * @param fileUrl 文件 URL
    // */
    // public void deleteFile(String fileUrl) {
    // try {
    // String bucket = properties.getDefaultBucket();
    // String objectKey = extractObjectKey(fileUrl);
    //
    // log.info("删除 RustFS 文件：bucket={}, objectKey={}", bucket, objectKey);
    //
    // s3Client.deleteObject(DeleteObjectRequest.builder()
    // .bucket(bucket)
    // .key(objectKey)
    // .build());
    //
    // log.info("文件删除成功：{}", fileUrl);
    //
    // } catch (Exception e) {
    // log.error("删除 RustFS 文件失败：{}", fileUrl, e);
    // throw new RuntimeException("删除文件失败：" + e.getMessage(), e);
    // }
    // }

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
     * 从 URL 中提取对象键
     */
    private String extractObjectKey(String fileUrl) {
        // 格式：http://host:port/bucket/objectKey
        String baseUrl = properties.getUrl();
        if (fileUrl.startsWith(baseUrl)) {
            String path = fileUrl.substring(baseUrl.length());
            // 移除开头的 bucket 名称
            int firstSlash = path.indexOf('/', 1);
            if (firstSlash > 0) {
                return path.substring(firstSlash + 1);
            }
        }
        // 如果无法解析，返回整个路径（作为备选方案）
        return fileUrl;
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
}
