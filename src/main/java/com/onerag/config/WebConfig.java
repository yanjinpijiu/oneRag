package com.onerag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Web 层基础配置。
 * 包含 SSE 线程池、CORS 与首页路由重定向。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 聊天流式输出专用线程池。
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService chatStreamExecutor() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger(1);

            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread t = new Thread(r, "chat-sse-" + n.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
        return Executors.newFixedThreadPool(8, factory);
    }

    /**
     * 允许前端跨域访问 API（开发阶段配置）。
     */
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    /**
     * 访问根路径时重定向到调试页 chat.html。
     */
    @Override
    public void addViewControllers(@NonNull ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/chat.html");
    }
}
