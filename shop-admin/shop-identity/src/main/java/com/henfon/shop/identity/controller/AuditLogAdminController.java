package com.henfon.shop.identity.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.entity.SysLoginLog;
import com.henfon.shop.identity.entity.SysOperLog;
import com.henfon.shop.identity.service.AuditLogService;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统登录与操作审计日志查询接口。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@RestController
@RequestMapping("/api/admin/audit")
public class AuditLogAdminController {

    private final AuditLogService auditLogService;

    /**
     * 创建审计日志控制器。
     *
     * @param auditLogService 审计日志服务
     * @author Henfon
     * @date 2026-08-31
     */
    public AuditLogAdminController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * 查询管理员登录日志。
     *
     * @param username 用户名关键字
     * @param status 登录状态
     * @param current 页码
     * @param size 页大小
     * @return 登录日志分页结果
     * @author Henfon
     * @date 2026-08-31
     */
    @GetMapping("/login-logs")
    @PreAuthorize("hasAuthority('system:audit:login')")
    public ApiResponse<IPage<SysLoginLog>> pageLogin(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(auditLogService.pageLogin(username, status, current, size), MDC.get("requestId"));
    }

    /**
     * 查询系统操作审计日志。
     *
     * @param username 操作用户名关键字
     * @param moduleKey 业务模块标识
     * @param current 页码
     * @param size 页大小
     * @return 操作日志分页结果
     * @author Henfon
     * @date 2026-08-31
     */
    @GetMapping("/operation-logs")
    @PreAuthorize("hasAuthority('system:audit:operation')")
    public ApiResponse<IPage<SysOperLog>> pageOperation(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String moduleKey,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(auditLogService.pageOperation(username, moduleKey, current, size), MDC.get("requestId"));
    }
}
