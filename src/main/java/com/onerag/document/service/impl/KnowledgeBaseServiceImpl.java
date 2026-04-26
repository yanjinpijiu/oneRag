package com.onerag.document.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.onerag.document.config.ChunkingConfig;
import com.onerag.document.dao.entity.KbDocumentDO;
import com.onerag.document.dao.mapper.KbDocumentMapper;
import com.onerag.document.dto.KbDocumentProcessReq;
import com.onerag.document.dto.KbDocumentQueryReq;
import com.onerag.document.dto.KbDocumentResp;
import com.onerag.document.dto.KbDocumentStatus;
import com.onerag.document.dto.KbDownloadUrlResp;
import com.onerag.document.dto.ParseResult;
import com.onerag.document.dto.TextChunk;
import com.onerag.document.dto.VectorChunk;
import com.onerag.document.service.ChunkingService;
import com.onerag.document.service.EmbeddingService;
import com.onerag.document.service.KnowledgeBaseService;
import com.onerag.document.service.MilvusVectorStoreService;
import com.onerag.document.service.RustFsStorageService;
import com.onerag.document.service.TikaParseService;
import com.onerag.user.dto.PageResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    private static final long DOWNLOAD_EXPIRES_SECONDS = 600L;

    private final KbDocumentMapper kbDocumentMapper;
    private final RustFsStorageService rustFsStorageService;
    private final TikaParseService tikaParseService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final MilvusVectorStoreService milvusVectorStoreService;

    @Override
    public PageResp<KbDocumentResp> page(KbDocumentQueryReq req) {
        long pageNo = req.getPageNo() == null || req.getPageNo() < 1 ? 1 : req.getPageNo();
        long pageSize = req.getPageSize() == null || req.getPageSize() < 1 ? 10 : Math.min(100, req.getPageSize());

        LambdaQueryWrapper<KbDocumentDO> wrapper = new LambdaQueryWrapper<>();
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            wrapper.eq(KbDocumentDO::getStatus, req.getStatus().trim());
        }
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            wrapper.like(KbDocumentDO::getFileName, req.getKeyword().trim());
        }
        wrapper.orderByDesc(KbDocumentDO::getUpdateTime);

        Page<KbDocumentDO> page = kbDocumentMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResp.<KbDocumentResp>builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .total(page.getTotal())
                .records(page.getRecords().stream().map(this::toResp).collect(Collectors.toList()))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbDocumentResp upload(MultipartFile file, String operator) {
        requireAdmin();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String filename = file.getOriginalFilename() == null ? "未命名文档" : file.getOriginalFilename();
        String fileExt = resolveFileExt(filename);
        String documentId = UUID.randomUUID().toString();

        RustFsStorageService.UploadResult uploadResult;
        try {
            uploadResult = rustFsStorageService.uploadFileDetailed(
                    file.getInputStream(),
                    filename,
                    file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                    null,
                    file.getSize());
        } catch (Exception e) {
            throw new IllegalArgumentException("上传文件失败: " + e.getMessage());
        }

        Date now = new Date();
        KbDocumentDO entity = KbDocumentDO.builder()
                .documentId(documentId)
                .fileName(filename)
                .fileExt(fileExt)
                .mimeType(file.getContentType())
                .fileSize(file.getSize())
                .s3Bucket(uploadResult.getBucket())
                .s3ObjectKey(uploadResult.getObjectKey())
                .fileUrl(uploadResult.getFileUrl())
                .status(KbDocumentStatus.UNPROCESSED)
                .chunkCount(0)
                .vectorCount(0)
                .errorMessage(null)
                .createdBy(operator)
                .updatedBy(operator)
                .createTime(now)
                .updateTime(now)
                .build();
        kbDocumentMapper.insert(entity);
        return toResp(entity);
    }

    @Override
    public KbDocumentResp detail(String documentId) {
        return toResp(getByDocumentId(documentId));
    }

    @Override
    public KbDocumentResp process(String documentId, KbDocumentProcessReq req, String operator) {
        requireAdmin();
        KbDocumentDO document = getByDocumentId(documentId);
        if (KbDocumentStatus.RUNNING.equals(document.getStatus())) {
            throw new IllegalArgumentException("当前文档正在处理中，请勿重复触发");
        }

        LambdaUpdateWrapper<KbDocumentDO> toRunning = new LambdaUpdateWrapper<>();
        toRunning.eq(KbDocumentDO::getDocumentId, documentId)
                .set(KbDocumentDO::getStatus, KbDocumentStatus.RUNNING)
                .set(KbDocumentDO::getErrorMessage, null)
                .set(KbDocumentDO::getUpdatedBy, operator)
                .set(KbDocumentDO::getUpdateTime, new Date());
        kbDocumentMapper.update(null, toRunning);

        try {
            byte[] fileBytes = rustFsStorageService.downloadFile(document.getS3Bucket(), document.getS3ObjectKey());
            ParseResult parseResult = tikaParseService.parseBytes(fileBytes, document.getFileName());
            if (!parseResult.isSuccess()) {
                throw new IllegalArgumentException(parseResult.getErrorMessage());
            }

            String strategy = req == null || req.getStrategy() == null || req.getStrategy().isBlank()
                    ? "fixed-size"
                    : req.getStrategy();
            int chunkSize = req == null || req.getChunkSize() == null ? 500 : req.getChunkSize();
            int overlapSize = req == null || req.getOverlapSize() == null ? 50 : req.getOverlapSize();
            ChunkingConfig chunkingConfig = new ChunkingConfig(chunkSize, overlapSize);
            List<TextChunk> chunks = chunkingService.chunk(parseResult.getContent(), strategy, chunkingConfig);
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("分片结果为空");
            }

            List<String> texts = chunks.stream().map(TextChunk::getContent).collect(Collectors.toList());
            List<List<Float>> embeddings = embeddingService.embedBatch(texts);
            if (embeddings.size() != chunks.size()) {
                throw new IllegalArgumentException("向量化结果数量与分片数量不一致");
            }

            // 重试或重建索引时先清理旧向量，避免重复写入
            milvusVectorStoreService.deleteDocumentChunks(documentId);

            List<VectorChunk> vectorChunks = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                TextChunk textChunk = chunks.get(i);
                List<Float> embedding = embeddings.get(i);
                vectorChunks.add(VectorChunk.builder()
                        .index(textChunk.getIndex())
                        .content(textChunk.getContent())
                        .chunkId(UUID.randomUUID().toString())
                        .embedding(toArray(embedding))
                        .build());
            }
            milvusVectorStoreService.indexDocumentChunks(documentId, document.getFileName(), vectorChunks);

            LambdaUpdateWrapper<KbDocumentDO> toCompleted = new LambdaUpdateWrapper<>();
            toCompleted.eq(KbDocumentDO::getDocumentId, documentId)
                    .set(KbDocumentDO::getStatus, KbDocumentStatus.COMPLETED)
                    .set(KbDocumentDO::getChunkCount, chunks.size())
                    .set(KbDocumentDO::getVectorCount, vectorChunks.size())
                    .set(KbDocumentDO::getErrorMessage, null)
                    .set(KbDocumentDO::getUpdatedBy, operator)
                    .set(KbDocumentDO::getUpdateTime, new Date());
            kbDocumentMapper.update(null, toCompleted);
            return detail(documentId);
        } catch (Exception e) {
            try {
                milvusVectorStoreService.deleteDocumentChunks(documentId);
            } catch (Exception cleanupEx) {
                log.warn("回滚 Milvus 失败, documentId={}", documentId, cleanupEx);
            }
            LambdaUpdateWrapper<KbDocumentDO> toFailed = new LambdaUpdateWrapper<>();
            toFailed.eq(KbDocumentDO::getDocumentId, documentId)
                    .set(KbDocumentDO::getStatus, KbDocumentStatus.FAILED)
                    .set(KbDocumentDO::getErrorMessage, safeError(e))
                    .set(KbDocumentDO::getUpdatedBy, operator)
                    .set(KbDocumentDO::getUpdateTime, new Date());
            kbDocumentMapper.update(null, toFailed);
            throw new IllegalArgumentException("处理失败: " + safeError(e));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String documentId, String operator) {
        requireAdmin();
        KbDocumentDO document = getByDocumentId(documentId);
        rustFsStorageService.deleteFile(document.getS3Bucket(), document.getS3ObjectKey());
        milvusVectorStoreService.deleteDocumentChunks(documentId);

        LambdaUpdateWrapper<KbDocumentDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(KbDocumentDO::getDocumentId, documentId)
                .set(KbDocumentDO::getUpdatedBy, operator)
                .set(KbDocumentDO::getUpdateTime, new Date());
        kbDocumentMapper.update(null, wrapper);
        LambdaQueryWrapper<KbDocumentDO> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(KbDocumentDO::getDocumentId, documentId);
        kbDocumentMapper.delete(deleteWrapper);
    }

    @Override
    public KbDownloadUrlResp getDownloadUrl(String documentId) {
        KbDocumentDO document = getByDocumentId(documentId);
        String url = rustFsStorageService.generatePresignedDownloadUrl(
                document.getS3Bucket(),
                document.getS3ObjectKey(),
                DOWNLOAD_EXPIRES_SECONDS);
        return KbDownloadUrlResp.builder()
                .url(url)
                .expiresInSeconds(DOWNLOAD_EXPIRES_SECONDS)
                .build();
    }

    private KbDocumentDO getByDocumentId(String documentId) {
        LambdaQueryWrapper<KbDocumentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocumentDO::getDocumentId, documentId);
        KbDocumentDO entity = kbDocumentMapper.selectOne(wrapper);
        if (entity == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        return entity;
    }

    private KbDocumentResp toResp(KbDocumentDO entity) {
        return KbDocumentResp.builder()
                .documentId(entity.getDocumentId())
                .fileName(entity.getFileName())
                .fileExt(entity.getFileExt())
                .mimeType(entity.getMimeType())
                .fileSize(entity.getFileSize())
                .status(entity.getStatus())
                .chunkCount(entity.getChunkCount())
                .vectorCount(entity.getVectorCount())
                .errorMessage(entity.getErrorMessage())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private String resolveFileExt(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private float[] toArray(List<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private void requireAdmin() {
        String role = String.valueOf(StpUtil.getTokenSession().get("role"));
        if (!"admin".equalsIgnoreCase(role)) {
            throw new IllegalArgumentException("无权限访问");
        }
    }

    private String safeError(Throwable e) {
        String message = e == null ? "未知错误" : e.getMessage();
        if (message == null || message.isBlank()) {
            message = "未知错误";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
