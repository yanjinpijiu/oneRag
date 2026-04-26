package com.onerag.chat.service;

/**
 * 查询改写服务
 */
public interface QueryRewriterService {
    
    /**
     * 改写用户查询
     * @param originalQuery 原始查询
     * @param context 上下文信息
     * @return 改写后的查询
     */
    String rewriteQuery(String originalQuery, String context);
}
