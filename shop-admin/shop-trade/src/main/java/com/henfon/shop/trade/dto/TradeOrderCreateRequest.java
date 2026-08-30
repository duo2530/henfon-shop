package com.henfon.shop.trade.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门户订单创建请求。
 *
 * @param memberId 会员ID
 * @param items 订单商品
 * @param receiverName 收货人
 * @param receiverPhone 收货电话
 * @param receiverProvince 省
 * @param receiverCity 市
 * @param receiverDistrict 区县
 * @param receiverAddress 详细地址
 * @param paymentMethod 支付方式
 * @param subtotalAmount 商品金额
 * @param discountAmount 优惠金额
 * @param freightAmount 运费
 * @param payableAmount 应付金额
 * @param idempotencyKey 订单幂等键
 * @author Henfon
 * @date 2026-08-29
 */
public record TradeOrderCreateRequest(@NotNull Long memberId, @NotEmpty List<@Valid Item> items,
                                      @NotBlank String receiverName, @NotBlank String receiverPhone,
                                      String receiverProvince, String receiverCity, String receiverDistrict,
                                      @NotBlank String receiverAddress, String paymentMethod,
                                      @NotNull BigDecimal subtotalAmount, @NotNull BigDecimal discountAmount,
                                      @NotNull BigDecimal freightAmount, @NotNull BigDecimal payableAmount,
                                      @Size(max = 64, message = "订单幂等键长度不能超过64") String idempotencyKey) {
    /**
     * 订单商品请求。
     *
     * @param productId 商品ID
     * @param skuId SKU ID
     * @param productName 商品名称
     * @param skuName SKU名称
     * @param skuCode SKU编码
     * @param imageUrl 商品图片
     * @param unitPrice 成交单价
     * @param quantity 购买数量
     * @author Henfon
     * @date 2026-08-29
     */
    public record Item(Long productId, Long skuId, @NotBlank String productName, String skuName,
                       String skuCode, String imageUrl, @NotNull BigDecimal unitPrice,
                       @NotNull @Min(1) Integer quantity) {
    }
}
