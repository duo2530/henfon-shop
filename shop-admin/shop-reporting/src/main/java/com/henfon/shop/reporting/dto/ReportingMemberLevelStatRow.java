package com.henfon.shop.reporting.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员等级分析数据库聚合行。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Data
public class ReportingMemberLevelStatRow {

    private String memberLevel;
    private Long memberCount;
    private Long activeMemberCount;
    private Long paidOrderCount;
    private BigDecimal paidAmount;
}
