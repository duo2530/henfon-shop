package com.henfon.shop.integration.logistics;

import java.util.List;

/**
 * 物流轨迹查询结果统一模型。
 *
 * @param success 是否查询成功
 * @param provider 服务商编码
 * @param carrierCode 物流公司编码
 * @param carrierName 物流公司名称
 * @param trackingNo 运单号
 * @param status 当前标准状态
 * @param message 结果说明
 * @param nodes 轨迹节点
 * @author Henfon
 * @date 2026-09-01
 */
public record LogisticsTrackResult(boolean success, String provider, String carrierCode,
                                   String carrierName, String trackingNo, String status,
                                   String message, List<LogisticsTrackNode> nodes) {

    /**
     * 创建查询失败结果。
     *
     * @param provider 服务商编码
     * @param trackingNo 运单号
     * @param message 失败说明
     * @return 失败结果
     * @author Henfon
     * @date 2026-09-01
     */
    public static LogisticsTrackResult failure(String provider, String trackingNo, String message) {
        return new LogisticsTrackResult(false, provider, null, null, trackingNo, "UNKNOWN", message, List.of());
    }
}
