package com.henfon.shop.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员登录日志实体。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private Integer loginStatus;
    private String loginIp;
    private String userAgent;
    private String failureReason;
    private LocalDateTime loginAt;
}
