package com.henfon.shop.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员余额与积分资产审计流水。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@Data
@TableName("member_asset_audit")
public class MemberAssetAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private Long pointsDelta;
    private Long pointsBefore;
    private Long pointsAfter;
    private BigDecimal balanceDelta;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String operation;
    private String remark;
    private LocalDateTime createdAt;
}
