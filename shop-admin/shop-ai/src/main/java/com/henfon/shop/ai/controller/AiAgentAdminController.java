package com.henfon.shop.ai.controller;

import com.henfon.shop.ai.dto.AiAgentDesk;
import com.henfon.shop.ai.dto.AiAgentEvent;
import com.henfon.shop.ai.dto.AiAgentMessage;
import com.henfon.shop.ai.dto.AiAgentProductDraft;
import com.henfon.shop.ai.dto.AiAgentReplyRequest;
import com.henfon.shop.ai.dto.AiAgentSessionDetail;
import com.henfon.shop.ai.dto.AiAgentSessionView;
import com.henfon.shop.ai.dto.AiAgentStatusRequest;
import com.henfon.shop.ai.dto.AiProductCard;
import com.henfon.shop.ai.dto.AiRatingSummary;
import com.henfon.shop.ai.dto.AiRatingView;
import com.henfon.shop.ai.dto.AiScheduleView;
import com.henfon.shop.ai.service.AiAgentService;
import com.henfon.shop.ai.service.AiAgentStatusService;
import com.henfon.shop.ai.service.AiProductCardService;
import com.henfon.shop.ai.service.AiRatingService;
import com.henfon.shop.ai.service.AiScheduleService;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 后台客服工作台接口。
 *
 * 接入方式是抢单式：等待队列对所有在线客服可见，谁先点「接入」谁接待。之所以不做自动分配，
 * 是因为客服人数少、每个人擅长的类目不同，自动派单会把问题分给不当的人，而客服不在电脑前
 * 时还会让会话堆在某个账号上。
 *
 * 一个权限点覆盖整组接口：能看队列却接不了会话的角色没有实际意义，拆开只会多出一组
 * 需要在每个新账号上配一次的权限。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@RestController
@RequestMapping("/api/admin/ai/agent")
public class AiAgentAdminController {

    private final AiAgentService agentService;

    private final AiAgentStatusService statusService;

    private final AiRatingService ratingService;

    private final AiScheduleService scheduleService;

    private final AiProductCardService productCardService;

    /**
     * 创建客服工作台控制器。
     *
     * @param agentService 人工接待服务
     * @param statusService 坐席状态服务
     * @param ratingService 评价服务
     * @param scheduleService 排班服务
     * @param productCardService 商品卡片装配服务，用于工作台的商品选择
     * @author Henfon
     * @date 2026-09-21
     */
    public AiAgentAdminController(AiAgentService agentService,
                                  AiAgentStatusService statusService,
                                  AiRatingService ratingService,
                                  AiScheduleService scheduleService,
                                  AiProductCardService productCardService) {
        this.agentService = agentService;
        this.statusService = statusService;
        this.ratingService = ratingService;
        this.scheduleService = scheduleService;
        this.productCardService = productCardService;
    }

    /**
     * 坐席状态卡。
     *
     * @param authentication 当前认证信息
     * @return 状态卡
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping("/desk")
    @PreAuthorize("hasAuthority('ai:agent:serve')")
    public ApiResponse<AiAgentDesk> desk(Authentication authentication) {
        return ApiResponse.success(statusService.desk(agentId(authentication)), requestId());
    }

    /**
     * 切换坐席状态：上线、小休、离线。
     *
     * @param request 目标状态
     * @param authentication 当前认证信息
     * @return 更新后的状态卡
     * @author Henfon
     * @date 2026-09-21
     */
    @PostMapping("/desk/status")
    @PreAuthorize("hasAuthority('ai:agent:serve')")
    public ApiResponse<AiAgentDesk> changeStatus(@Valid @RequestBody AiAgentStatusRequest request,
                                                 Authentication authentication) {
        return ApiResponse.success(statusService.changeStatus(agentId(authentication), request.status()),
                requestId());
    }

    /**
     * 坐席心跳。
     *
     * 工作台打开期间定频调用，客服没点下线就关掉页面时，靠心跳过期把他判为离线。
     *
     * @param authentication 当前认证信息
     * @return 更新后的状态卡
     * @author Henfon
     * @date 2026-09-21
     */
    @PostMapping("/desk/heartbeat")
    @PreAuthorize("hasAuthority('ai:agent:serve')")
    public ApiResponse<AiAgentDesk> heartbeat(Authentication authentication) {
        return ApiResponse.success(statusService.heartbeat(agentId(authentication)), requestId());
    }

    /**
     * 我的本周班次。
     *
     * 客服自己要看班，但排班管理是主管的权限，所以单独开一个只读接口给工作台用。
     *
     * @param authentication 当前认证信息
     * @return 排班列表，按星期升序
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping("/my-schedule")
    @PreAuthorize("hasAuthority('ai:agent:serve')")
    public ApiResponse<List<AiScheduleView>> mySchedule(Authentication authentication) {
        return ApiResponse.success(scheduleService.weeklySchedule(agentId(authentication)), requestId());
    }

    /**
     * 评价统计。
     *
     * @param agentId 只看某位客服，不传表示全部
     * @param authentication 当前认证信息
     * @return 统计
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping("/ratings/summary")
    @PreAuthorize("hasAuthority('ai:agent:serve')")
    public ApiResponse<AiRatingSummary> ratingSummary(@RequestParam(required = false) Long agentId,
                                                      Authentication authentication) {
        return ApiResponse.success(ratingService.summary(agentId == null ? agentId(authentication) : agentId),
                requestId());
    }

    /**
     * 最近收到的评价。
     *
     * @param agentId 只看某位客服，不传表示自己
     * @param limit 返回条数上限
     * @param authentication 当前认证信息
     * @return 评价列表
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping("/ratings")
    @PreAuthorize("hasAuthority('ai:agent:serve')")
    public ApiResponse<List<AiRatingView>> ratings(@RequestParam(required = false) Long agentId,
                                                   @RequestParam(defaultValue = "10") int limit,
                                                   Authentication authentication) {
        return ApiResponse.success(ratingService.recent(agentId == null ? agentId(authentication) : agentId, limit),
                requestId());
    }

    /**
     * 等待人工接入的会话队列。
     *
     * @return 会话列表
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping("/queue")
    @PreAuthorize("hasAuthority('ai:agent:serve')")
    public ApiResponse<List<AiAgentSessionView>> queue() {
        return ApiResponse.success(agentService.waitingQueue(), requestId());
    }

    /**
     * 当前客服正在接待的会话。
     *
     * @param authentication 当前认证信息
     * @return 会话列表
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('ai:agent:serve')")
    public ApiResponse<List<AiAgentSessionView>> sessions(Authentication authentication) {
        return ApiResponse.success(agentService.mySessions(agentId(authentication)), requestId());
    }

    /**
     * 会话详情。
     *
     * @param conversationId 会话标识
     * @param authentication 当前认证信息
     * @return 详情
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping("/sessions/{conversationId}")
    @PreAuthorize("hasAuthority('ai:agent:serve')")
    public ApiResponse<AiAgentSessionDetail> detail(@PathVariable String conversationId,
                                                    Authentication authentication) {
        return ApiResponse.success(agentService.detail(conversationId, agentId(authentication)), requestId());
    }

    /**
     * 接入会话。
     *
     * @param conversationId 会话标识
     * @param authentication 当前认证信息
     * @return 会话视图
     * @author Henfon
     * @date 2026-09-21
     */
    @PostMapping("/sessions/{conversationId}/join")
    @PreAuthorize("hasAuthority('ai:agent:serve')")
    public ApiResponse<AiAgentSessionView> join(@PathVariable String conversationId,
                                                Authentication authentication) {
        return ApiResponse.success(agentService.join(conversationId, agentId(authentication)), requestId());
    }

    /**
     * 回复买家。
     *
     * @param conversationId 会话标识
     * @param request 回复请求
     * @param authentication 当前认证信息
     * @return 落库后的消息
     * @author Henfon
     * @date 2026-09-21
     */
    @PostMapping("/sessions/{conversationId}/messages")
    @PreAuthorize("hasAuthority('ai:agent:serve')")
    public ApiResponse<AiAgentMessage> send(@PathVariable String conversationId,
                                            @Valid @RequestBody AiAgentReplyRequest request,
                                            Authentication authentication) {
        AiAgentMessage message = agentService.agentSend(conversationId, agentId(authentication), request.content());
        return ApiResponse.success(message, requestId());
    }

    /**
     * 搜索可推送的商品。
     *
     * 客服在工作台发商品卡片前要先找到商品，这里复用门户的商品搜索，只返回装配好的卡片：
     * 图片地址已经换发过，工作台拿到的就是买家将要看到的那张图，选错图的概率因此低得多。
     *
     * @param keyword 关键词
     * @param limit 返回条数上限
     * @return 商品卡片
     * @author Henfon
     * @date 2026-09-22
     */
    @GetMapping("/products")
    @PreAuthorize("hasAuthority('ai:agent:serve')")
    public ApiResponse<List<AiProductCard>> products(@RequestParam String keyword,
                                                     @RequestParam(defaultValue = "6") int limit) {
        return ApiResponse.success(productCardService.search(keyword, limit), requestId());
    }

    /**
     * 推送商品卡片给买家。
     *
     * @param conversationId 会话标识
     * @param draft 商品卡片草稿
     * @param authentication 当前认证信息
     * @return 落库后的消息
     * @author Henfon
     * @date 2026-09-22
     */
    @PostMapping("/sessions/{conversationId}/products")
    @PreAuthorize("hasAuthority('ai:agent:serve')")
    public ApiResponse<AiAgentMessage> sendProduct(@PathVariable String conversationId,
                                                   @RequestBody AiAgentProductDraft draft,
                                                   Authentication authentication) {
        AiAgentMessage message = agentService.agentSendProduct(conversationId, agentId(authentication), draft);
        return ApiResponse.success(message, requestId());
    }

    /**
     * 结束人工接待，会话退回智能客服。
     *
     * @param conversationId 会话标识
     * @param authentication 当前认证信息
     * @return 会话视图
     * @author Henfon
     * @date 2026-09-21
     */
    @PostMapping("/sessions/{conversationId}/end")
    @PreAuthorize("hasAuthority('ai:agent:serve')")
    public ApiResponse<AiAgentSessionView> end(@PathVariable String conversationId,
                                               Authentication authentication) {
        return ApiResponse.success(agentService.end(conversationId, agentId(authentication)), requestId());
    }

    /**
     * 订阅会话消息流。
     *
     * @param conversationId 会话标识
     * @param authentication 当前认证信息
     * @return 事件流
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping(value = "/sessions/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    @PreAuthorize("hasAuthority('ai:agent:serve')")
    public Flux<AiAgentEvent> stream(@PathVariable String conversationId, Authentication authentication) {
        return agentService.subscribeForAgent(conversationId, agentId(authentication));
    }

    /**
     * 从认证上下文取管理员 ID。
     *
     * @param authentication 当前认证信息
     * @return 管理员 ID，未登录返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    private Long agentId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return user.userId();
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
