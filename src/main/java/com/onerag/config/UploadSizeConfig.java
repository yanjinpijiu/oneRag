package com.onerag.config;

import jakarta.servlet.MultipartConfigElement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * 强制放宽上传限制：仅靠 application.yml 在部分环境下仍可能未绑定到 Tomcat Connector /
 * Servlet MultipartConfig，导致小文件也触发 MaxUploadSizeExceededException。
 */
@Slf4j
@Configuration
public class UploadSizeConfig {

    /** 与 application.yml 中 500MB 一致 */
    private static final DataSize MAX_UPLOAD = DataSize.ofMegabytes(500);

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatMaxPostSizeCustomizer() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            int maxBytes = (int) Math.min(MAX_UPLOAD.toBytes(), Integer.MAX_VALUE);
            connector.setMaxPostSize(maxBytes);
            log.info("Tomcat Connector maxPostSize 已设置为 {} 字节 (~{} MB)",
                    maxBytes, maxBytes / 1024 / 1024);
        });
    }

    /**
     * 显式注册 MultipartConfig（存在该 Bean 时由 Spring Boot 用于 DispatcherServlet），
     * 避免仅依赖属性绑定的差异。
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(MAX_UPLOAD);
        factory.setMaxRequestSize(MAX_UPLOAD);
        MultipartConfigElement element = factory.createMultipartConfig();
        log.info("MultipartConfig: maxFileSize={}, maxRequestSize={}", MAX_UPLOAD, MAX_UPLOAD);
        return element;
    }
}
