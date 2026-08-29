package com.henfon.shop.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 后台订单发货请求。
 *
 * @author Henfon
 * @date 2026-08-29
 */
public record TradeOrderShipRequest(
        @NotBlank(message = "物流公司不能为空")
        @Size(max = 64, message = "物流公司长度不能超过64个字符")
        String logisticsCompany,
        @NotBlank(message = "物流单号不能为空")
        @Size(max = 128, message = "物流单号长度不能超过128个字符")
        String trackingNo) {
}
