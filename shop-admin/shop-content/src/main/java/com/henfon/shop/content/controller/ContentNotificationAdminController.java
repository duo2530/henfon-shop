package com.henfon.shop.content.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.content.dto.ContentNotificationAdminItem;
import com.henfon.shop.content.dto.ContentNotificationAdminSummary;
import com.henfon.shop.content.service.ContentNotificationAdminService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台通知中心接口，供顶栏通知入口浏览平台发出的会员通知。
 *
 * <p>这里的通知是发给会员的站内信投递记录，运营在后台只读浏览并标记跟进状态，
 * 不能借此改写会员端的阅读状态。</p>
 *
 * @author Henfon
 * @date 2026-09-18
 */
@RestController
@RequestMapping("/api/admin/content/notifications")
public class ContentNotificationAdminController {

    private final ContentNotificationAdminService notificationService;

    /**
     * 创建后台通知中心控制器。
     *
     * @param notificationService 通知中心应用服务
     * @author Henfon
     * @date 2026-09-18
     */
    public ContentNotificationAdminController(ContentNotificationAdminService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 分页查询平台发出的会员通知。
     *
     * @param eventType 事件类型，可选
     * @param adminReadStatus 运营已读状态，可选
     * @param memberReadStatus 会员已读状态，可选
     * @param keyword 标题、内容或业务单号关键字，可选
     * @param memberId 收件会员ID，可选
     * @param current 当前页
     * @param size 页大小
     * @return 通知分页数据
     * @author Henfon
     * @date 2026-09-18
     */
    @GetMapping
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<IPage<ContentNotificationAdminItem>> page(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @Min(0) @Max(1) Integer adminReadStatus,
            @RequestParam(required = false) @Min(0) @Max(1) Integer memberReadStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long memberId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(notificationService.page(eventType, adminReadStatus, memberReadStatus,
                keyword, memberId, current, size), requestId());
    }

    /**
     * 统计通知总数与未读数，供顶栏红点与抽屉标题使用。
     *
     * @return 通知统计概览
     * @author Henfon
     * @date 2026-09-18
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<ContentNotificationAdminSummary> summary() {
        return ApiResponse.success(notificationService.summary(), requestId());
    }

    /**
     * 将一条通知标记为运营已读。
     *
     * @param id 通知ID
     * @return 空响应
     * @author Henfon
     * @date 2026-09-18
     */
    @PutMapping("/{id}/read")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ApiResponse.success(requestId());
    }

    /**
     * 将全部运营未读通知标记为已读。
     *
     * @return 本次更新条数
     * @author Henfon
     * @date 2026-09-18
     */
    @PutMapping("/read-all")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<Integer> markAllRead() {
        return ApiResponse.success(notificationService.markAllRead(), requestId());
    }

    /**
     * 获取请求链路标识。
     *
     * @return 请求链路标识
     * @author Henfon
     * @date 2026-09-18
     */
    private String requestId() {
        return MDC.get("requestId");
    }
}
