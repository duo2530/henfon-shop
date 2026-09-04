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

    /**
     * 判断 MinIO 连接配置是否完整。
     *
     * @return 配置是否可用
     * @author Henfon
     * @date 2026-09-04
     */
    public boolean isConfigured() {
        // 四项配置缺失时统一走业务兜底，避免出现难以定位的底层异常。
        return hasText(endpoint) && hasText(accessKey) && hasText(secretKey) && hasText(bucket);
    }

    /**
     * 判断字符串是否包含有效文本。
     *
     * @param value 待校验值
     * @return 是否包含有效文本
     * @author Henfon
     * @date 2026-09-04
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
