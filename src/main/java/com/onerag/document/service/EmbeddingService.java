package com.onerag.document.service;

import java.util.List;

/**
 * 向量化服务抽象。
 */
public interface EmbeddingService {

    /**
     * 单文本向量化。
     */
    List<Float> embed(String text);

    /**
     * 批量文本向量化。
     */
    List<List<Float>> embedBatch(List<String> texts);

    /**
     * 向量维度（默认与当前模型一致）。
     */
    default int dimension() {
        return 4096;
    }
}
