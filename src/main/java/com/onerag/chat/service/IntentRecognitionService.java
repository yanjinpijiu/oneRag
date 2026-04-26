package com.onerag.chat.service;

/**
 * 意图识别服务
 */
public interface IntentRecognitionService {
    
    /**
     * 识别用户意图
     * @param query 改写后的查询
     * @param context 上下文信息
     * @return 意图识别结果
     */
    String recognizeIntent(String query, String context);
}
