package com.henfon.shop.trade.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门户运费试算请求。
 *
 * @param items 待计算商品
 * @param receiverProvince 收货省
 * @param receiverCity 收货市
 * @param receiverDistrict 收货区县
 * @param subtotalAmount 商品金额
 * @param discountAmount 优惠金额
 * @author Henfon
 * @date 2026-09-01
 */
public record TradeFreightQuoteRequest(@NotEmpty List<@Valid Item> items,
                                       String receiverProvince,
                                       String receiverCity,
                                       String receiverDistrict,
                                       @NotNull @jakarta.validation.constraints.DecimalMin("0.00") BigDecimal subtotalAmount,
                                       @NotNull @jakarta.validation.constraints.DecimalMin("0.00") BigDecimal discountAmount) {

    /**
     * 运费试算商品。
     *
     * @param productId 商品ID
     * @param skuId SKU ID
     * @param quantity 购买数量
     * @author Henfon
     * @date 2026-09-01
     */
    public record Item(@NotNull Long productId, Long skuId, @NotNull @Min(1) Integer quantity) {
    }
}
