package com.henfon.shop.trade.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 运费模板保存请求。
 *
 * @param id 模板ID，新增时为空
 * @param templateName 模板名称
 * @param carrierName 承运商名称
 * @param baseWeightGram 首重克数
 * @param baseFee 首重费用
 * @param additionalWeightGram 续重计费克数
 * @param additionalFee 续重费用
 * @param freeShippingThreshold 包邮门槛
 * @param remoteSurcharge 偏远地区附加费
 * @param remoteRegionsCsv 偏远地区关键词
 * @param status 模板状态
 * @author Henfon
 * @date 2026-09-01
 */
public record TradeFreightTemplateSaveRequest(Long id,
                                               @NotBlank @Size(max = 64) String templateName,
                                               @NotBlank @Size(max = 64) String carrierName,
                                               @NotNull @Min(1) Integer baseWeightGram,
                                               @NotNull @DecimalMin("0.00") BigDecimal baseFee,
                                               @NotNull @Min(1) Integer additionalWeightGram,
                                               @NotNull @DecimalMin("0.00") BigDecimal additionalFee,
                                               @NotNull @DecimalMin("0.00") BigDecimal freeShippingThreshold,
                                               @NotNull @DecimalMin("0.00") BigDecimal remoteSurcharge,
                                               @Size(max = 2000) String remoteRegionsCsv,
                                               @NotNull Integer status) {
}
