package com.henfon.shop.marketing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

/**
 * 秒杀活动预热与状态收口定时任务。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@Component
public class MarketingFlashSaleWarmupScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketingFlashSaleWarmupScheduler.class);

    private final MarketingFlashSaleService flashSaleService;

    @Value("${shop.marketing.flash-sale-warmup-minutes:30}")
    private int warmupMinutes;

    /**
     * 创建秒杀预热调度器。
     *
     * @param flashSaleService 秒杀活动服务
     * @author Henfon
     * @date 2026-09-04
     */
    public MarketingFlashSaleWarmupScheduler(MarketingFlashSaleService flashSaleService) {
        this.flashSaleService = flashSaleService;
    }

    /**
     * 扫描即将开始的活动并执行库存预热。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Scheduled(fixedDelayString = "${shop.marketing.flash-sale-warmup-scan-ms:60000}",
            initialDelayString = "${shop.marketing.flash-sale-warmup-initial-delay-ms:15000}")
    public void warmup() {
        try {
            int processed = flashSaleService.warmupUpcomingActivities(warmupMinutes);
            log.debug("秒杀活动预热扫描完成，processed={}", processed);
        } catch (RuntimeException exception) {
            // 单轮预热异常不影响后续调度，下一轮继续重试并通过日志暴露问题。
            log.error("秒杀活动预热扫描失败", exception);
        }
    }
}
