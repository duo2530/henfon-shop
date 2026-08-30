package com.henfon.shop.inventory.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 库存预占过期补偿定时任务。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Component
public class InventoryReservationExpiryScheduler {
    private final InventoryReservationExpiryService expiryService;

    /**
     * 创建库存预占过期补偿定时任务。
     *
     * @param expiryService 过期补偿服务
     * @author Henfon
     * @date 2026-08-30
     */
    public InventoryReservationExpiryScheduler(InventoryReservationExpiryService expiryService) {
        this.expiryService = expiryService;
    }

    /**
     * 定时扫描并释放过期库存预占。
     *
     * @author Henfon
     * @date 2026-08-30
     */
    @Scheduled(fixedDelayString = "${shop.inventory.expire-scan-ms:60000}",
            initialDelayString = "${shop.inventory.expire-initial-delay-ms:30000}")
    public void compensateExpiredLocks() {
        expiryService.compensate(200);
    }
}
