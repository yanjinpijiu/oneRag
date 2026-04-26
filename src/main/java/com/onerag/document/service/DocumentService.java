package com.onerag.document.service;

import com.onerag.document.config.ChunkingConfig;
import com.onerag.document.dto.ParseResult;
import com.onerag.document.dto.TextChunk;
import com.onerag.document.dto.VectorChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文档处理服务
 * 协调 TikaParseService、RustFsStorageService 和 ChunkingService 完成文档处理流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final TikaParseService tikaParseService;
    private final RustFsStorageService rustFsStorageService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final MilvusVectorStoreService milvusVectorStoreService;

    /**
     * 解析文件并上传到 RustFS
     *
     * @param file 上传的文件
     * @return 解析结果（包含 RustFS 文件 URL）
     */
    public ParseResult parseAndUploadToFile(MultipartFile file) {
        try {
            // 1. 使用 Tika 解析文件，提取文本和元数据
            ParseResult parseResult = tikaParseService.parseFile(file);
            if (!parseResult.isSuccess()) {
                return parseResult;
            }

            // 2. 将原始文件上传到 RustFS
            String mimeType = parseResult.getMimeType();
            String fileUrl = rustFsStorageService.uploadFile(
                    file.getInputStream(),
                    file.getOriginalFilename(),
                    mimeType != null ? mimeType : "application/octet-stream",
                    parseResult.getMetadata(),
                    file.getSize());
            parseResult.setFileUrl(fileUrl);

            log.info("文件上传到 RustFS 成功：{}", fileUrl);
            return parseResult;

        } catch (Exception e) {
            log.error("文件上传失败：{}", file.getOriginalFilename(), e);
            return ParseResult.failure("文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 解析文件、上传到 RustFS 并进行分块
     *
     * @param file        上传的文件
     * @param strategy    分块策略
     * @param chunkSize   分块大小
     * @param overlapSize 重叠大小
     * @return 解析结果（包含分块和 RustFS 文件 URL）
     */
    public ParseResult parseUploadAndChunk(
            MultipartFile file,
            String strategy,
            int chunkSize,
            int overlapSize) {
        try {
            // 1. 使用 Tika 解析文件，提取文本和元数据
            ParseResult parseResult = tikaParseService.parseFile(file);
            if (!parseResult.isSuccess()) {
                return parseResult;
            }

            // 2. 将原始文件上传到 RustFS
            String mimeType = parseResult.getMimeType();
            String fileUrl = rustFsStorageService.uploadFile(
                    file.getInputStream(),
                    file.getOriginalFilename(),
                    mimeType != null ? mimeType : "application/octet-stream",
                    parseResult.getMetadata(),
                    file.getSize());
            parseResult.setFileUrl(fileUrl);
            log.info("文件上传到 RustFS 成功：{}", fileUrl);

            // 3. 对文本进行分块
            ChunkingConfig chunkingConfig = new ChunkingConfig(chunkSize, overlapSize);
            List<TextChunk> chunks = chunkingService.chunk(parseResult.getContent(), strategy, chunkingConfig);
            parseResult.setChunks(chunks);
            parseResult.setChunkingStrategy(strategy);
            parseResult.setChunkCount(chunks.size());
            parseResult.setChunkingConfig(chunkingConfig);

            log.info("文件处理完成：{}, 分块数：{}", file.getOriginalFilename(), chunks.size());
            return parseResult;

        } catch (Exception e) {
            log.error("文件处理失败：{}", file.getOriginalFilename(), e);
            return ParseResult.failure("文件处理失败：" + e.getMessage());
        }
    }

    /**
     * 仅解析文件，返回文本和元数据（不存储到 RustFS）
     *
     * @param file 上传的文件
     * @return 解析结果
     */
    public ParseResult parseFile(MultipartFile file) {
        return tikaParseService.parseFile(file);
    }

    /**
     * 检测文件的 MIME 类型
     *
     * @param file 上传的文件
     * @return MIME 类型字符串
     * @throws IOException IO 异常
     */
    public String detectMimeType(MultipartFile file) throws IOException {
        return tikaParseService.detectMimeType(file);
    }

    /**
     * 解析文件、上传到 RustFS、分块、向量化并存储到 Milvus
     *
     * @param file        上传的文件
     * @param strategy    分块策略
     * @param chunkSize   分块大小
     * @param overlapSize 重叠大小
     * @return 解析结果（包含分块、向量和 RustFS 文件 URL）
     */
    public ParseResult parseUploadChunkAndEmbed(
            MultipartFile file,
            String strategy,
            int chunkSize,
            int overlapSize) {
        try {
            // 1. 使用 Tika 解析文件，提取文本和元数据
            ParseResult parseResult = tikaParseService.parseFile(file);
            if (!parseResult.isSuccess()) {
                return parseResult;
            }

            // 2. 将原始文件上传到 RustFS
            String mimeType = parseResult.getMimeType();
            String fileUrl = rustFsStorageService.uploadFile(
                    file.getInputStream(),
                    file.getOriginalFilename(),
                    mimeType != null ? mimeType : "application/octet-stream",
                    parseResult.getMetadata(),
                    file.getSize());
            parseResult.setFileUrl(fileUrl);
            log.info("文件上传到 RustFS 成功：{}", fileUrl);

            // 3. 对文本进行分块
            ChunkingConfig chunkingConfig = new ChunkingConfig(chunkSize, overlapSize);
            List<TextChunk> chunks = chunkingService.chunk(parseResult.getContent(), strategy, chunkingConfig);
            parseResult.setChunks(chunks);
            parseResult.setChunkingStrategy(strategy);
            parseResult.setChunkCount(chunks.size());
            parseResult.setChunkingConfig(chunkingConfig);

            // 4. 对分块进行向量化
            log.info("开始对 {} 个分块进行向量化", chunks.size());
            List<String> chunkTexts = chunks.stream()
                    .map(TextChunk::getContent)
                    .collect(Collectors.toList());

            List<List<Float>> embeddings = embeddingService.embedBatch(chunkTexts);

            if (embeddings.size() != chunks.size()) {
                log.error("向量化结果数量不匹配：期望 {}, 实际 {}", chunks.size(), embeddings.size());
                return ParseResult.failure("向量化失败：结果数量不匹配");
            }

            // 5. 构建向量分块并存储到 Milvus
            String docId = UUID.randomUUID().toString();
            List<VectorChunk> vectorChunks = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {
                TextChunk textChunk = chunks.get(i);
                List<Float> embedding = embeddings.get(i);

                VectorChunk vectorChunk = VectorChunk.builder()
                        .index(textChunk.getIndex())
                        .content(textChunk.getContent())
                        .chunkId(UUID.randomUUID().toString())
                        .embedding(toArray(embedding))
                        .build();

                vectorChunks.add(vectorChunk);
            }

            String docTitle = file.getOriginalFilename() != null ? file.getOriginalFilename() : "未命名文档";
            milvusVectorStoreService.indexDocumentChunks(docId, docTitle, vectorChunks);
            log.info("成功将 {} 个向量分块存储到 Milvus，文档ID: {}", vectorChunks.size(), docId);

            log.info("文件处理完成：{}, 分块数：{}, 向量化完成", file.getOriginalFilename(), chunks.size());
            return parseResult;

        } catch (Exception e) {
            log.error("文件处理失败：{}", file.getOriginalFilename(), e);
            return ParseResult.failure("文件处理失败：" + e.getMessage());
        }
    }

    private float[] toArray(List<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}
