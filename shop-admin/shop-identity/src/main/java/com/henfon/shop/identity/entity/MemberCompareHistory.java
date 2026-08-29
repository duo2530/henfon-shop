package com.henfon.shop.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员商品对比历史实体。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Data
@TableName("member_compare_history")
public class MemberCompareHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private LocalDateTime comparedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
}
