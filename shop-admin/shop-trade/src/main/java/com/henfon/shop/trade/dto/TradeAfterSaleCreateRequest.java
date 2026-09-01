package com.henfon.shop.trade.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门户售后申请请求。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record TradeAfterSaleCreateRequest(
        Long orderItemId,
        @NotNull(message = "售后类型不能为空")
        Integer afterSaleType,
        @DecimalMin(value = "0.00", message = "退款金额不能为负数")
        BigDecimal refundAmount,
        @NotBlank(message = "售后原因不能为空")
        @Size(max = 500, message = "售后原因长度不能超过500个字符")
        String reason,
        @Size(max = 9, message = "售后凭证最多上传9张")
        List<@NotBlank(message = "售后凭证地址不能为空")
                @Size(max = 512, message = "售后凭证地址不能超过512个字符") String> evidenceUrls) {
}
