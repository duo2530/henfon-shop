package com.henfon.shop.ai.controller;

import com.henfon.shop.ai.dto.AiAgentAvailability;
import com.henfon.shop.ai.dto.AiAgentEvent;
import com.henfon.shop.ai.dto.AiAgentMessage;
import com.henfon.shop.ai.dto.AiAgentMessageRequest;
import com.henfon.shop.ai.dto.AiAgentSessionView;
import com.henfon.shop.ai.dto.AiAgentState;
import com.henfon.shop.ai.dto.AiAgentTransferRequest;
import com.henfon.shop.ai.dto.AiPendingRating;
import com.henfon.shop.ai.dto.AiRatingRequest;
import com.henfon.shop.ai.dto.AiRatingView;
import com.henfon.shop.ai.service.AiAgentService;
import com.henfon.shop.ai.service.AiAgentStatusService;
import com.henfon.shop.ai.service.AiRatingService;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 门户转人工接口。
 *
 * 与前缀下的智能客服接口共用 {@code /api/portal/ai}，但对访客不是全开放的：转人工必须有
 * 会员身份。访客没有归属，客服回复时对方多半已经关掉页面，会话会变成单向的独白；而且
 * 匿名会话无法做归属校验，任何拿到会话标识的人都能读走别人的对话。
 *
 * 长连接返回 SSE 而不是轮询：客服接入、买家消息到达都需要尽快送达，轮询要么延迟高、
 * 要么把数据库查穿。事件里的消息同时已经落库，断线重连由快照补齐。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@RestController
@RequestMapping("/api/portal/ai/agent")
public class AiAgentPortalController {

    private final AiAgentService agentService;

    private final AiAgentStatusService statusService;

    private final AiRatingService ratingService;

    /**
     * 创建门户转人工控制器。
     *
     * @param agentService 人工接待服务
     * @param statusService 坐席状态服务
     * @param ratingService 评价服务
     * @author Henfon
     * @date 2026-09-21
     */
    public AiAgentPortalController(AiAgentService agentService,
                                   AiAgentStatusService statusService,
                                   AiRatingService ratingService) {
        this.agentService = agentService;
        this.statusService = statusService;
        this.ratingService = ratingService;
    }

    /**
     * 查询当前是否可以转人工。
     *
     * 免登录：买家要先知道"现在有没有人接"才好决定是找客服还是直接留言，这个判断不需要身份。
     * 只返回人数与文案，不暴露坐席名单。
     *
     * @return 可用性
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping("/availability")
    public ApiResponse<AiAgentAvailability> availability() {
        return ApiResponse.success(statusService.availability(), requestId());
    }

    /**
     * 提交会话评价。
     *
     * @param request 评价内容
     * @param authentication 当前认证信息
     * @return 评价
     * @author Henfon
     * @date 2026-09-21
     */
    @PostMapping("/rating")
    public ApiResponse<AiRatingView> rate(@Valid @RequestBody AiRatingRequest request,
                                         Authentication authentication) {
        AiRatingView view = ratingService.submit(memberId(authentication), request);
        return ApiResponse.success(view, requestId());
    }

    /**
     * 查询待补的评价。
     *
     * 客服结束会话时买家可能已经关掉页面，评价不能只在那一刻弹一次。买家下次打开客服窗口
     * 时用它把还没评的那次服务补上。
     *
     * @param authentication 当前认证信息
     * @return 待评价会话，没有则 data 为空
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping("/rating/pending")
    public ApiResponse<AiPendingRating> pendingRating(Authentication authentication) {
        return ApiResponse.success(ratingService.pending(memberId(authentication)), requestId());
    }

    /**
     * 请求转人工。
     *
     * @param request 转人工请求
     * @param authentication 当前认证信息
     * @return 会话视图
     * @author Henfon
     * @date 2026-09-21
     */
    @PostMapping("/request")
    public ApiResponse<AiAgentSessionView> request(@Valid @RequestBody AiAgentTransferRequest request,
                                                   Authentication authentication) {
        AiAgentSessionView view = agentService.requestAgent(memberId(authentication), request.conversationId());
        return ApiResponse.success(view, requestId());
    }

    /**
     * 发送消息。
     *
     * @param request 消息请求
     * @param authentication 当前认证信息
     * @return 落库后的消息
     * @author Henfon
     * @date 2026-09-21
     */
    @PostMapping("/messages")
    public ApiResponse<AiAgentMessage> send(@Valid @RequestBody AiAgentMessageRequest request,
                                            Authentication authentication) {
        AiAgentMessage message = agentService.memberSend(request.conversationId(), memberId(authentication),
                request.content());
        return ApiResponse.success(message, requestId());
    }

    /**
     * 买家主动结束本次人工咨询。
     *
     * 客服忘点「结束服务」时（去处理别的会话、临时被叫走、下班、直接关了浏览器），会话会一直
     * 挂在人工接待上：买家提问被拦住、智能客服也用不了。自动回收要等空闲阈值，这里给买家一个
     * 立刻能用的出口。
     *
     * 与客服侧结束区分开：这条路径不校验"是不是接管的客服"，但仍校验归属——只有会话主人能
     * 结束自己的咨询。
     *
     * @param request 结束请求
     * @param authentication 当前认证信息
     * @return 会话视图
     * @author Henfon
     * @date 2026-09-22
     */
    @PostMapping("/end")
    public ApiResponse<AiAgentSessionView> end(@Valid @RequestBody AiAgentTransferRequest request,
                                                Authentication authentication) {
        AiAgentSessionView view = agentService.memberEnd(request.conversationId(), memberId(authentication));
        return ApiResponse.success(view, requestId());
    }

    /**
     * 查询会话接待状态。
     *
     * 买家刷新页面后用它恢复上一次的会话，不必重新描述一遍问题。
     *
     * @param conversationId 会话标识
     * @param authentication 当前认证信息
     * @return 接待状态
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping("/state")
    public ApiResponse<AiAgentState> state(@RequestParam String conversationId, Authentication authentication) {
        return ApiResponse.success(agentService.state(conversationId, memberId(authentication)), requestId());
    }

    /**
     * 订阅会话消息流。
     *
     * 归属校验在返回流之前同步完成，所以「会话不存在、不属于当前账号」会走正常的 HTTP 错误
     * 响应，前端不必为长连接单独维护一套错误处理。
     *
     * @param conversationId 会话标识
     * @param authentication 当前认证信息
     * @return 事件流
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<AiAgentEvent> stream(@RequestParam String conversationId, Authentication authentication) {
        return agentService.subscribeForMember(conversationId, memberId(authentication));
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
