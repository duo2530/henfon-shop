package com.henfon.shop.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 每日销售趋势聚合点。
 *
 * @param date 统计日期
 * @param salesAmount 当日有效销售额
 * @param orderCount 当日已支付订单数
 * @param productQuantity 当日已支付商品销量
 * @author Henfon
 * @date 2026-08-31
 */
@Getter
@AllArgsConstructor
public class ReportingSalesTrendPoint {

    private final LocalDate date;
    private final BigDecimal salesAmount;
    private final long orderCount;
    private final long productQuantity;
}
