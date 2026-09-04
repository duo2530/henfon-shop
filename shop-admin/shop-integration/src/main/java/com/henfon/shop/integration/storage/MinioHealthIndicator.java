package com.henfon.shop.integration.storage;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * MinIO 存储服务健康检查及运行指标。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@Component("minio")
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioStorageService storageService;

    /**
     * 创建 MinIO 健康检查器。
     *
     * @param storageService MinIO 文件服务
     * @author Henfon
     * @date 2026-09-04
     * @描述 注入文件服务以复用连通性探测和运行指标
     */
    public MinioHealthIndicator(MinioStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 执行 MinIO 健康检查并返回运行指标。
     *
     * @return MinIO 健康状态
     * @author Henfon
     * @date 2026-09-04
     * @描述 检查默认桶连通性，输出成功/失败及最近故障信息
     */
    @Override
    public Health health() {
        // 每次调用只执行一次轻量探测，避免对 MinIO 造成额外压力。
        boolean healthy = storageService.checkHealth();
        Health.Builder builder = healthy ? Health.up() : Health.down();
        builder.withDetail("configured", storageService.isConfigured())
                .withDetail("operationSuccessCount", storageService.getOperationSuccessCount())
                .withDetail("operationFailureCount", storageService.getOperationFailureCount())
                .withDetail("consecutiveFailureCount", storageService.getConsecutiveFailureCount())
                .withDetail("lastFailureAt", storageService.getLastFailureAt());
        if (storageService.getLastFailureMessage() != null) {
            builder.withDetail("lastFailureMessage", storageService.getLastFailureMessage());
        }
        return builder.build();
    }
}
