package com.henfon.shop.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统操作审计日志实体。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private Long userId;
    private String username;
    private String moduleKey;
    private String operation;
    private String requestMethod;
    private String requestUri;
    private String requestParams;
    private Integer responseStatus;
    private String clientIp;
    private Long durationMs;
    private LocalDateTime createdAt;
}
