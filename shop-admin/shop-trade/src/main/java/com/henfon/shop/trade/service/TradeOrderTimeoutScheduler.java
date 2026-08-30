package com.henfon.shop.trade.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 待付款订单超时关闭任务。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Component
public class TradeOrderTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(TradeOrderTimeoutScheduler.class);

    private final TradeOrderService tradeOrderService;

    @Value("${shop.trade.order-payment-timeout-minutes:30}")
    private int timeoutMinutes;

    /**
     * 创建订单超时任务。
     *
     * @param tradeOrderService 订单应用服务
     * @author Henfon
     * @date 2026-08-30
     */
    public TradeOrderTimeoutScheduler(TradeOrderService tradeOrderService) {
        this.tradeOrderService = tradeOrderService;
    }

    /**
     * 定时关闭超时未支付订单并释放库存。
     *
     * @author Henfon
     * @date 2026-08-30
     */
    @Scheduled(fixedDelayString = "${shop.trade.order-timeout-scan-ms:60000}", initialDelayString = "${shop.trade.order-timeout-initial-delay-ms:15000}")
    public void closeExpiredOrders() {
        int closedCount = tradeOrderService.closeExpiredOrders(Math.max(timeoutMinutes, 1));
        if (closedCount > 0) {
            log.info("订单超时关闭任务完成，关闭订单数={}", closedCount);
        }
    }
}
