package com.henfon.shop.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.ai.dto.AiTicketHandleRequest;
import com.henfon.shop.ai.entity.AiTicket;
import com.henfon.shop.ai.service.AiTicketService;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台客服工单接口。
 *
 * 查询与处理分成两个权限点：只看不动的账号（客服主管巡检、数据分析）不该同时拿到关闭
 * 工单的能力，工单状态一改，买家侧的回访预期就跟着变。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@RestController
@RequestMapping("/api/admin/ai/tickets")
public class AiTicketAdminController {

    private final AiTicketService ticketService;

    /**
     * 创建后台工单控制器。
     *
     * @param ticketService 工单服务
     * @author Henfon
     * @date 2026-09-21
     */
    public AiTicketAdminController(AiTicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * 分页查询工单。
     *
     * @param status 处理状态，可为空
     * @param keyword 联系方式或问题关键字，可为空
     * @param current 当前页
     * @param size 页大小
     * @return 分页数据
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ai:ticket:query')")
    public ApiResponse<IPage<AiTicket>> page(@RequestParam(required = false) String status,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(ticketService.page(status, keyword, current, size), requestId());
    }

    /**
     * 查询工单详情。
     *
     * @param id 工单 ID
     * @return 工单
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:ticket:query')")
    public ApiResponse<AiTicket> detail(@PathVariable Long id) {
        return ApiResponse.success(ticketService.detail(id), requestId());
    }

    /**
     * 处理工单。
     *
     * @param id 工单 ID
     * @param request 处理请求
     * @param authentication 当前认证信息
     * @return 更新后的工单
     * @author Henfon
     * @date 2026-09-21
     */
    @PostMapping("/{id}/handle")
    @PreAuthorize("hasAuthority('ai:ticket:handle')")
    public ApiResponse<AiTicket> handle(@PathVariable Long id,
                                       @Valid @RequestBody AiTicketHandleRequest request,
                                       Authentication authentication) {
        AuthenticatedUser operator = authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user ? user : null;
        Long handlerId = operator == null ? null : operator.userId();
        String handlerName = operator == null ? null : operator.username();
        return ApiResponse.success(ticketService.handle(id, request, handlerId, handlerName), requestId());
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
