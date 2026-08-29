package com.henfon.shop.trade.dto;

import jakarta.validation.constraints.Size;

/**
 * 后台订单取消请求。
 *
 * @author Henfon
 * @date 2026-08-29
 */
public record TradeOrderCancelRequest(
        @Size(max = 500, message = "取消原因长度不能超过500个字符")
        String reason) {
}
