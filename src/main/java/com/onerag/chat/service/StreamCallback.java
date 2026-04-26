package com.onerag.chat.service;

/**
 * 模型流式输出回调。
 * 将首包、推理片段、正文片段与结束/错误事件解耦给上层控制器处理。
 */
public interface StreamCallback {

    /**
     * 首个 token 到达时触发（常用于统计 TTFB）。
     */
    void onFirstToken();

    /**
     * 推理内容增量回调（仅在模型返回 reasoning 时触发）。
     */
    default void onReasoning(String reasoning) {
    }

    /**
     * 正文内容增量回调。
     */
    void onContent(String content);

    /**
     * 正常结束回调。
     */
    void onComplete();

    /**
     * 异常结束回调。
     */
    void onError(Throwable error);
}
