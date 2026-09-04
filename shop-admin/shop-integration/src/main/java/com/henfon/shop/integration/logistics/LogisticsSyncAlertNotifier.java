package com.henfon.shop.integration.logistics;
/**
 * 物流同步失败告警通知端口。
 * @author Henfon
 * @date 2026-09-04
 */
@FunctionalInterface
public interface LogisticsSyncAlertNotifier {
    /**
     * 发送物流同步达到重试上限的告警。
     * @param orderId 订单ID @param orderNo 订单号 @param logisticsCompany 物流公司
     * @param trackingNo 脱敏运单号 @param attempts 已尝试次数 @param errorMessage 最近一次错误信息
     * @author Henfon @date 2026-09-04
     */
    void notifyMaxRetry(Long orderId, String orderNo, String logisticsCompany, String trackingNo, int attempts, String errorMessage);
}
