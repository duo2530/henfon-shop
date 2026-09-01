package com.henfon.shop.trade.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 后台订单卖家备注请求。
 *
 * @author Henfon
 * @date 2026-08-29
 */
public record TradeOrderRemarkRequest(
        @Size(max = 500, message = "卖家备注长度不能超过500个字符")
        String sellerRemark,
        @Size(max = 16, message = "订单标旗颜色格式不正确")
        String flagColor,
        @NotNull(message = "订单版本不能为空")
        @Min(value = 0, message = "订单版本不能为负数")
        Integer version) {
}
