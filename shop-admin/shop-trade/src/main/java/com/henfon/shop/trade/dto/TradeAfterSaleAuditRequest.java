package com.henfon.shop.trade.dto;

import jakarta.validation.constraints.Size;

/**
 * 后台售后审核请求。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record TradeAfterSaleAuditRequest(
        @Size(max = 500, message = "审核备注长度不能超过500个字符")
        String remark) {
}
