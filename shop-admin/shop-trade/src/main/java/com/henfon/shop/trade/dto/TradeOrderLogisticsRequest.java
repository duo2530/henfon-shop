package com.henfon.shop.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 后台订单物流节点追加或更新请求。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public record TradeOrderLogisticsRequest(
        Long id,
        @NotBlank(message = "物流公司不能为空")
        @Size(max = 64, message = "物流公司长度不能超过64个字符")
        String logisticsCompany,
        @NotBlank(message = "物流单号不能为空")
        @Size(max = 128, message = "物流单号长度不能超过128个字符")
        String trackingNo,
        @Size(max = 64, message = "物流状态长度不能超过64个字符")
        String logisticsStatus,
        @NotNull(message = "物流节点时间不能为空")
        LocalDateTime eventTime,
        @NotBlank(message = "物流节点描述不能为空")
        @Size(max = 500, message = "物流节点描述长度不能超过500个字符")
        String eventDescription,
        @Size(max = 200, message = "物流节点地点长度不能超过200个字符")
        String eventLocation,
        Integer sortNo) {
}
