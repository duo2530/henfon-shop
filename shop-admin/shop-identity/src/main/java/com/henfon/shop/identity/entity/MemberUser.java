package com.henfon.shop.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门户会员实体。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Data
@TableName("member_user")
public class MemberUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String memberNo;
    private String username;
    @JsonIgnore
    private String passwordHash;
    private String nickname;
    private String phone;
    private String email;
    private String avatarUrl;
    private String memberLevel;
    private Long points;
    private BigDecimal balance;
    private Integer status;
    private LocalDateTime registeredAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
    private String remark;
}
