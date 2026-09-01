package com.henfon.shop.integration.logistics;

import java.time.LocalDateTime;

/**
 * 物流轨迹节点统一模型。
 *
 * @param eventTime 节点时间
 * @param status 标准物流状态
 * @param description 节点描述
 * @param location 节点位置
 * @author Henfon
 * @date 2026-09-01
 */
public record LogisticsTrackNode(LocalDateTime eventTime, String status,
                                 String description, String location) {
}
