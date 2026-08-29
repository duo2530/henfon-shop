package com.henfon.shop.integration.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 对象存储连接配置。
 *
 * @param endpoint MinIO 服务地址
 * @param accessKey 访问密钥
 * @param secretKey 私有密钥
 * @param bucket 默认存储桶
 * @author Henfon
 * @date 2026-08-29
 */
@ConfigurationProperties(prefix = "shop.storage.minio")
public record MinioProperties(String endpoint, String accessKey, String secretKey, String bucket) {
}
