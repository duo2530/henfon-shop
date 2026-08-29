package com.henfon.shop.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台系统用户实体。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String username;
    @JsonIgnore
    private String passwordHash;
    private String realName;
    private String nickname;
    private String phone;
    private String email;
    private String avatarUrl;
    private Long deptId;
    private Integer status;
    private String userType;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime passwordUpdatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
    private String remark;
}
