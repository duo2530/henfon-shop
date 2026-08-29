package com.henfon.shop.integration.storage;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfiguration {

    /**
     * 创建单例 MinIO 客户端。
     *
     * @param properties MinIO 连接配置
     * @return MinIO 客户端
     * @author Henfon
     * @date 2026-08-29
     */
    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        // 客户端只负责连接，存储桶初始化和文件业务由后续适配器负责。
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
