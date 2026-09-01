package com.henfon.shop.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.integration.logistics.LogisticsProvider;
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
    private final TradeOrderMapper tradeOrderMapper;
    private final TradeOrderService tradeOrderService;
    private final LogisticsProvider logisticsProvider;

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
                                       LogisticsProvider logisticsProvider) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.tradeOrderService = tradeOrderService;
        this.logisticsProvider = logisticsProvider;
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
            try {
                tradeOrderService.syncLogistics(order.getId());
            } catch (RuntimeException exception) {
                // 单个物流服务商失败不能阻塞其他订单，本轮由下一次扫描重试。
                LOGGER.warn("订单物流同步失败，orderId={}, trackingNo={}", order.getId(), maskTrackingNo(order.getTrackingNo()), exception);
            }
        }
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
