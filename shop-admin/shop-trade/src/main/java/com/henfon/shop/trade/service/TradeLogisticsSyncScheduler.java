package com.henfon.shop.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.integration.logistics.LogisticsProvider;
import com.henfon.shop.integration.logistics.LogisticsSyncAlertNotifier;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.mapper.TradeOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 已发货订单物流轨迹定时同步任务。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Component
public class TradeLogisticsSyncScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeLogisticsSyncScheduler.class);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private final TradeOrderMapper tradeOrderMapper;
    private final TradeOrderService tradeOrderService;
    private final LogisticsProvider logisticsProvider;
    private final LogisticsSyncAlertNotifier alertNotifier;

    /**
     * 创建物流同步任务。
     *
     * @param tradeOrderMapper 订单数据访问对象
     * @param tradeOrderService 订单应用服务
     * @param logisticsProvider 物流服务商
     * @author Henfon
     * @date 2026-09-01
     */
    public TradeLogisticsSyncScheduler(TradeOrderMapper tradeOrderMapper,
                                       TradeOrderService tradeOrderService,
                                       LogisticsProvider logisticsProvider,
                                       LogisticsSyncAlertNotifier alertNotifier) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.tradeOrderService = tradeOrderService;
        this.logisticsProvider = logisticsProvider;
        this.alertNotifier = alertNotifier;
    }

    /**
     * 扫描并同步一批仍在运输中的订单。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Scheduled(fixedDelayString = "${shop.trade.logistics-sync-scan-ms:600000}",
            initialDelayString = "${shop.trade.logistics-sync-initial-delay-ms:30000}")
    public void syncActiveOrders() {
        if (!logisticsProvider.enabled()) {
            return;
        }
        List<TradeOrder> orders = tradeOrderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderStatus, TradeOrderStateMachine.STATUS_SHIPPED)
                .isNotNull(TradeOrder::getLogisticsCompany)
                .isNotNull(TradeOrder::getTrackingNo)
                .orderByAsc(TradeOrder::getShippedAt)
                .last("LIMIT 100"));
        for (TradeOrder order : orders) {
            if (!StringUtils.hasText(order.getLogisticsCompany()) || !StringUtils.hasText(order.getTrackingNo())) {
                continue;
            }
            syncOrderWithRetry(order);
        }
    }

    /**
     * 同步单笔订单并对查询失败或无轨迹结果执行有限重试。
     *
     * @param order 待同步订单
     * @author Henfon
     * @date 2026-09-04
     */
    private void syncOrderWithRetry(TradeOrder order) {
        int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                var result = tradeOrderService.syncLogistics(order.getId());
                // 服务商返回成功但没有节点，通常意味着暂无轨迹，需继续重试避免静默丢失。
                if (result.syncedCount() > 0 || "SIGNED".equalsIgnoreCase(result.status())) {
                    return;
                }
                LOGGER.warn("订单物流暂无轨迹，准备重试，orderId={}, attempt={}/{}, trackingNo={}",
                        order.getId(), attempt, maxAttempts, maskTrackingNo(order.getTrackingNo()));
            } catch (RuntimeException exception) {
                lastException = exception;
                LOGGER.warn("订单物流同步失败，准备重试，orderId={}, attempt={}/{}, trackingNo={}",
                        order.getId(), attempt, maxAttempts, maskTrackingNo(order.getTrackingNo()), exception);
            }
        }
        // 达到重试上限后输出结构化错误日志，供日志平台配置告警通知。
        LOGGER.error("订单物流同步告警，已达到重试上限，orderId={}, trackingNo={}",
                order.getId(), maskTrackingNo(order.getTrackingNo()), lastException);
        alertNotifier.notifyMaxRetry(order.getId(), order.getOrderNo(), order.getLogisticsCompany(),
                maskTrackingNo(order.getTrackingNo()), maxAttempts,
                lastException == null ? "暂无物流轨迹" : lastException.getMessage());
    }

    /**
     * 脱敏日志中的运单号。
     *
     * @param trackingNo 原始运单号
     * @return 脱敏后的运单号
     * @author Henfon
     * @date 2026-09-01
     */
    private String maskTrackingNo(String trackingNo) {
        if (!StringUtils.hasText(trackingNo)) {
            return "";
        }
        String value = trackingNo.trim();
        if (value.length() <= 6) {
            return "***";
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }
}
