package com.henfon.shop.trade.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.trade.entity.TradeEventOutbox;
import com.henfon.shop.trade.service.TradeEventOutboxCompensationService;
import com.henfon.shop.identity.security.AuthenticatedUser;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 交易事件 Outbox 死信运维接口。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@RestController
@RequestMapping("/api/admin/trade/outbox")
public class TradeEventOutboxAdminController {

    private final TradeEventOutboxCompensationService compensationService;

    /**
     * 创建 Outbox 死信运维控制器。
     *
     * @param compensationService Outbox 死信补偿服务
     * @author Henfon
     * @date 2026-08-31
     */
    public TradeEventOutboxAdminController(TradeEventOutboxCompensationService compensationService) {
        this.compensationService = compensationService;
    }

    /**
     * 分页查询 Outbox 死信事件。
     *
     * @param eventType 事件类型
     * @param topic RocketMQ 主题
     * @param aggregateId 业务聚合 ID
     * @param current 当前页
     * @param size 页大小
     * @return 死信事件分页结果
     * @author Henfon
     * @date 2026-08-31
     */
    @GetMapping("/dead-events")
    @PreAuthorize("hasAuthority('trade:outbox:query')")
    public ApiResponse<IPage<TradeEventOutbox>> pageDeadEvents(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(compensationService.pageDeadEvents(eventType, topic, aggregateId, current, size),
                MDC.get("requestId"));
    }

    /**
     * 人工重试指定死信事件。
     *
     * @param eventId 事件唯一标识
     * @return 已重新排队的 Outbox 事件
     * @author Henfon
     * @date 2026-08-31
     */
    @PostMapping("/{eventId}/retry")
    @PreAuthorize("hasAuthority('trade:outbox:retry')")
    public ApiResponse<TradeEventOutbox> retryDeadEvent(@PathVariable String eventId,
                                                        Authentication authentication) {
        return ApiResponse.success(compensationService.retryDeadEvent(eventId, operatorName(authentication)),
                MDC.get("requestId"));
    }

    /**
     * 从认证主体中提取人工补偿操作人名称。
     *
     * @param authentication 当前管理员认证信息
     * @return 操作人用户名
     * @author Henfon
     * @date 2026-09-04
     */
    private String operatorName(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser user) {
            return user.username();
        }
        return authentication.getName();
    }
}
