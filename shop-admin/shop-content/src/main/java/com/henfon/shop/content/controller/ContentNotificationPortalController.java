package com.henfon.shop.content.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.content.entity.ContentNotification;
import com.henfon.shop.content.service.ContentNotificationService;
import com.henfon.shop.identity.security.MemberPrincipalResolver;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门户会员站内通知接口。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@RestController
@RequestMapping("/api/portal/content/notifications")
public class ContentNotificationPortalController {

    private final ContentNotificationService notificationService;

    /**
     * 创建门户通知控制器。
     *
     * @param notificationService 通知应用服务
     * @author Henfon
     * @date 2026-08-31
     */
    public ContentNotificationPortalController(ContentNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 分页查询当前会员的站内通知。
     *
     * @param memberId 请求会员 ID，可为空
     * @param readStatus 已读状态，0 未读、1 已读
     * @param current 当前页
     * @param size 页大小
     * @param authentication 当前认证信息
     * @return 通知分页数据
     * @author Henfon
     * @date 2026-08-31
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<IPage<ContentNotification>> page(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) @Min(0) @Max(1) Integer readStatus,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            Authentication authentication) {
        Long currentMemberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        return ApiResponse.success(notificationService.pageForMember(currentMemberId, readStatus, current, size), requestId());
    }

    /**
     * 将当前会员的一条通知标记为已读。
     *
     * @param notificationId 通知 ID
     * @param authentication 当前认证信息
     * @return 空响应
     * @author Henfon
     * @date 2026-08-31
     */
    @PutMapping("/{notificationId}/read")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> markRead(@PathVariable Long notificationId, Authentication authentication) {
        notificationService.markRead(MemberPrincipalResolver.requireMemberId(authentication), notificationId);
        return ApiResponse.success(requestId());
    }

    /**
     * 将当前会员的全部未读通知标记为已读。
     *
     * @param authentication 当前认证信息
     * @return 更新数量
     * @author Henfon
     * @date 2026-08-31
     */
    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Integer> markAllRead(Authentication authentication) {
        return ApiResponse.success(notificationService.markAllRead(
                MemberPrincipalResolver.requireMemberId(authentication)), requestId());
    }

    /**
     * 获取请求链路标识。
     *
     * @return 请求链路标识
     * @author Henfon
     * @date 2026-08-31
     */
    private String requestId() {
        return MDC.get("requestId");
    }
}
