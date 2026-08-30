package com.henfon.shop.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员消费统计实体。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Data
@TableName("member_consumption_stat")
public class MemberConsumptionStat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private Long paidOrderCount;
    private BigDecimal paidAmount;
    private LocalDateTime lastOrderAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;
    private String remark;
}
