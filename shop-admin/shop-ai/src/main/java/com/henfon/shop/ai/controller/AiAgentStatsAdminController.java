package com.henfon.shop.ai.controller;

import com.henfon.shop.ai.dto.AiAgentStatsBoard;
import com.henfon.shop.ai.dto.AiAgentStatsDetail;
import com.henfon.shop.ai.service.AiAgentStatsService;
import com.henfon.shop.common.api.ApiResponse;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
     * 一位客服的详细服务数据。
     *
     * 列表一行的每个数字点进去都能拆开：接待量按天分布、每次会话花了多久、买家具体怎么评的。
     * 排在列表后面是为了让人先看整体再挑人细看，所以它不是独立的页面权限，复用列表那个权限点。
     *
     * @param agentId 客服管理员 ID
     * @param days 统计窗口天数，不传取近 30 天，传 0 表示全部历史
     * @return 详情
     * @author Henfon
     * @date 2026-09-22
     */
    @GetMapping("/{agentId}")
    @PreAuthorize("hasAuthority('ai:agent:stats:query')")
    public ApiResponse<AiAgentStatsDetail> detail(@PathVariable Long agentId,
                                                  @RequestParam(required = false) Integer days) {
        return ApiResponse.success(statsService.detail(agentId, days), requestId());
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
