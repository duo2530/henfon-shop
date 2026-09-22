package com.henfon.shop.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.ai.dto.AiTicketPortalView;
import com.henfon.shop.ai.dto.AiTicketSubmitRequest;
import com.henfon.shop.ai.entity.AiTicket;
import com.henfon.shop.ai.service.AiTicketService;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 门户转人工工单接口。
 *
 * 提交对访客开放：答不出来的时候正是最需要转人工的时候，而这其中相当一部分人没有登录。
 * 要求先登录才能留言，等于在这条兜底通道上再加一道门。
 *
 * 查询则要求登录，且只能看自己的：匿名提交的工单没有归属，谁拿着工单号都能查到内容与回复，
 * 与其做一个含糊的"凭编号查"入口，不如让买家登录后再看——提交时留下的联系方式也正好派上用场。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@RestController
@RequestMapping("/api/portal/ai/tickets")
public class AiTicketPortalController {

    private final AiTicketService ticketService;

    /**
     * 创建门户工单控制器。
     *
     * @param ticketService 工单服务
     * @author Henfon
     * @date 2026-09-21
     */
    public AiTicketPortalController(AiTicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * 提交转人工工单。
     *
     * 只回传工单编号与提交时间，不回传整条工单：买家需要的是能报给客服的编号，工单里的
     * 其他字段（处理人、备注）属于内部信息。
     *
     * @param request 提交请求
     * @param authentication 当前认证信息，访客时为空
     * @return 工单编号与提交时间
     * @author Henfon
     * @date 2026-09-21
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> submit(@Valid @RequestBody AiTicketSubmitRequest request,
                                                   Authentication authentication) {
        AiTicket ticket = ticketService.submit(memberId(authentication), request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticketNo", ticket.getTicketNo());
        data.put("createdAt", ticket.getCreatedAt());
        return ApiResponse.success(data, MDC.get("requestId"));
    }

    /**
     * 查询自己提交过的工单。
     *
     * @param current 当前页
     * @param size 页大小
     * @param authentication 当前认证信息
     * @return 分页数据
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping
    public ApiResponse<IPage<AiTicketPortalView>> myTickets(@RequestParam(defaultValue = "1") long current,
                                                            @RequestParam(defaultValue = "10") long size,
                                                            Authentication authentication) {
        IPage<AiTicketPortalView> page = ticketService.pageByMember(memberId(authentication), current, size)
                .convert(AiTicketPortalView::from);
        return ApiResponse.success(page, MDC.get("requestId"));
    }

    /**
     * 查询自己某张工单的详情。
     *
     * @param id 工单 ID
     * @param authentication 当前认证信息
     * @return 工单详情
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping("/{id}")
    public ApiResponse<AiTicketPortalView> myTicketDetail(@PathVariable Long id, Authentication authentication) {
        AiTicket ticket = ticketService.findOwnedTicket(memberId(authentication), id);
        return ApiResponse.success(AiTicketPortalView.from(ticket), MDC.get("requestId"));
    }

    /**
     * 从认证上下文取会员 ID。
     *
     * @param authentication 当前认证信息
     * @return 会员 ID，未登录或非会员身份返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    private Long memberId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return "MEMBER".equals(user.userType()) ? user.userId() : null;
    }
}
