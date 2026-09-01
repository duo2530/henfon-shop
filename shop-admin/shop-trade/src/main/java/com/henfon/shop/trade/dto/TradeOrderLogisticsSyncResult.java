package com.henfon.shop.trade.dto;

/**
 * 订单物流同步结果。
 *
 * @param success 是否同步成功
 * @param provider 物流服务商编码
 * @param status 当前物流状态
 * @param syncedCount 本次新增或命中的节点数量
 * @param message 同步说明
 * @author Henfon
 * @date 2026-09-01
 */
public record TradeOrderLogisticsSyncResult(boolean success, String provider, String status,
                                            int syncedCount, String message) {
}
