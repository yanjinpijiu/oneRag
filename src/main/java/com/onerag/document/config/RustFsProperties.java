package com.onerag.document.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RustFS 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "rustfs")
public class RustFsProperties {
    
    /**
     * RustFS 服务地址
     */
    private String url;
    
    /**
     * 访问密钥 ID
     */
    private String accessKeyId;
    
    /**
     * 访问密钥密码
     */
    private String secretAccessKey;
    
    /**
     * 默认存储桶名称
     */
    private String bucket ;
}
