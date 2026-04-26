package com.onerag.document.service.impl;

import com.onerag.document.dto.RetrievedChunk;
import com.onerag.document.service.BM25RetrieverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * BM25 检索服务占位实现。
 * 当前仅保留接口形态，后续可替换为真实倒排索引实现。
 */
@Slf4j
@Service
public class BM25RetrieverServiceImpl implements BM25RetrieverService {

    @Override
    public void buildIndex() {
        // 暂时为空实现，等待依赖问题解决
        log.info("BM25索引构建完成（临时实现）");
    }

    @Override
    public List<RetrievedChunk> retrieve(String query, int topK) {
        // 暂时返回空列表，等待依赖问题解决
        log.info("BM25检索完成，找到 0 个结果（临时实现）");
        return new ArrayList<>();
    }
}
