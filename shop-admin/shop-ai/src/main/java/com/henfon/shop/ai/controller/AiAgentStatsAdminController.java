package com.henfon.shop.ai.controller;

import com.henfon.shop.ai.dto.AiAgentStatsBoard;
import com.henfon.shop.ai.service.AiAgentStatsService;
import com.henfon.shop.common.api.ApiResponse;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客服统计接口。
 *
 * 单独一个权限点（ai:agent:stats:query）而不是复用接待权限：这一页看的是"每位客服接了多少、
 * 接了多久、被评了几分"，摆在同事之间就是个排行榜。默认不发给客服角色，需要的主管角色
 * 单独授予即可——接待权限与看别人的服务数据是两件事。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@RestController
@RequestMapping("/api/admin/ai/agent/stats")
public class AiAgentStatsAdminController {

    private final AiAgentStatsService statsService;

    /**
     * 创建客服统计控制器。
     *
     * @param statsService 统计服务
     * @author Henfon
     * @date 2026-09-21
     */
    public AiAgentStatsAdminController(AiAgentStatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * 客服服务记录统计。
     *
     * @param days 统计窗口天数，不传取近 30 天，传 0 表示全部历史
     * @return 看板数据
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ai:agent:stats:query')")
    public ApiResponse<AiAgentStatsBoard> board(@RequestParam(required = false) Integer days) {
        return ApiResponse.success(statsService.board(days), requestId());
    }

    /**
     * 获取请求链路标识。
     *
     * @return 请求链路标识
     * @author Henfon
     * @date 2026-09-21
     */
    private String requestId() {
        return MDC.get("requestId");
    }
}
