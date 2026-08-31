package com.henfon.shop.reporting.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 数据库每日销售趋势聚合行。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Data
public class ReportingSalesTrendRow {

    private LocalDate date;
    private BigDecimal salesAmount;
    private Long orderCount;
    private Long productQuantity;
}
