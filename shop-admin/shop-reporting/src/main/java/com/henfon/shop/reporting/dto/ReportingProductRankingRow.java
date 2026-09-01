package com.henfon.shop.reporting.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品排行数据库聚合行。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Data
public class ReportingProductRankingRow {

    private Long productId;
    private String productName;
    private String categoryName;
    private Long salesVolume;
    private BigDecimal salesAmount;
    private Long orderCount;
}
