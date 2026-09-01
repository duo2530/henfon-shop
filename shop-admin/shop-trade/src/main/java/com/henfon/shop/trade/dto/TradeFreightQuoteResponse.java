package com.henfon.shop.trade.dto;

import java.math.BigDecimal;

/**
 * 运费试算响应。
 *
 * @param templateId 模板ID
 * @param templateName 模板名称
 * @param carrierName 承运商名称
 * @param freightAmount 运费金额
 * @param totalWeightGram 总重量（克）
 * @param freeShipping 是否满足包邮
 * @param remoteArea 是否命中偏远地区
 * @author Henfon
 * @date 2026-09-01
 */
public record TradeFreightQuoteResponse(Long templateId, String templateName, String carrierName,
                                        BigDecimal freightAmount, Integer totalWeightGram,
                                        boolean freeShipping, boolean remoteArea) {
}
