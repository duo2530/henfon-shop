package com.henfon.shop.trade.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 后台订单批量发货请求。
 *
 * @param shipments 批量发货明细
 * @author Henfon
 * @date 2026-09-01
 */
public record TradeOrderBatchShipRequest(
        @NotEmpty(message = "批量发货订单不能为空")
        @Size(max = 100, message = "单次最多发货100笔订单")
        List<@Valid Item> shipments) {

    /**
     * 单笔批量发货明细。
     *
     * @param orderId 订单ID
     * @param logisticsCompany 物流公司
     * @param trackingNo 物流单号
     * @author Henfon
     * @date 2026-09-01
     */
    public record Item(
            @NotNull(message = "订单ID不能为空")
            Long orderId,
            @NotBlank(message = "物流公司不能为空")
            @Size(max = 64, message = "物流公司长度不能超过64个字符")
            String logisticsCompany,
            @NotBlank(message = "物流单号不能为空")
            @Size(max = 128, message = "物流单号长度不能超过128个字符")
            String trackingNo) {
    }
}
