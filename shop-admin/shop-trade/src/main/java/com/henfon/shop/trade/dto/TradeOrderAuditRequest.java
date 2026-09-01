package com.henfon.shop.trade.dto;

import jakarta.validation.constraints.Size;

/**
 * 后台订单审核请求。
 *
 * @author Henfon
 * @date 2026-09-01
 */
public record TradeOrderAuditRequest(
        @Size(max = 500, message = "审核备注长度不能超过500个字符")
        String remark,
        Integer version) {
}
