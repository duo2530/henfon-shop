package com.henfon.shop.ai.controller;

import com.henfon.shop.ai.dto.AiChatMessage;
import com.henfon.shop.ai.dto.AiChatRequest;
import com.henfon.shop.ai.entity.AiConversation;
import com.henfon.shop.ai.entity.AiMessage;
import com.henfon.shop.ai.service.AiChatRateLimiter;
import com.henfon.shop.ai.service.AiChatService;
import com.henfon.shop.ai.service.AiConversationService;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门户在线客服接口。
 *
 * 整个前缀对访客开放：没登录也能问商品与规则类问题，是方案里的明确要求。登录态由安全
 * 过滤器在令牌存在时写入上下文，所以这个接口既能拿到会员身份，也不会因为缺少令牌而拒绝。
 *
 * 对话接口返回 SSE 流而不是统一响应体。统一响应体要在整段回答生成完之后才能组装，那等于
 * 把流式输出的意义抹掉；流里的每一段都是独立的 JSON，错误也走事件，前端只需要一套解析。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@RestController
@RequestMapping("/api/portal/ai")
public class AiPortalController {

    private static final Logger log = LoggerFactory.getLogger(AiPortalController.class);

    private final ObjectProvider<AiChatService> chatServiceProvider;

    private final AiConversationService conversationService;

    private final AiChatRateLimiter rateLimiter;

    /**
     * 创建门户客服控制器。
     *
     * @param chatServiceProvider 对话编排服务，AI 未启用时为空
     * @param conversationService 会话管理服务
     * @param rateLimiter 提问配额计数器
     * @author Henfon
     * @date 2026-09-21
     */
    public AiPortalController(ObjectProvider<AiChatService> chatServiceProvider,
                              AiConversationService conversationService,
                              AiChatRateLimiter rateLimiter) {
        this.chatServiceProvider = chatServiceProvider;
        this.conversationService = conversationService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 查询客服是否可用。
     *
     * 门户根据它决定要不要挂出客服入口：AI 未启用的环境里摆一个点开只报错的悬浮按钮，
     * 比没有入口更糟。
     *
     * @return 可用状态
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", chatServiceProvider.getIfAvailable() != null);
        return ApiResponse.success(data, requestId());
    }

    /**
     * 提问并流式接收回答。
     *
     * 配额拒绝与"客服未启用"都走 error 事件而不是 429 状态码：本接口的错误协议是"流里每段
     * 都是独立 JSON"，保持单一解析路径比让前端为前置拒绝单独写一个分支更划算，前端拿到 code
     * 就能分辨是哪种情况。其余业务异常（会话不存在、会话不属于当前账号）仍由统一异常处理器
     * 返回 HTTP 错误——那些在推流之前就抛出了，本来就有状态码可用。
     *
     * @param request 提问请求
     * @param authentication 当前认证信息，访客时为空
     * @param servletRequest 当前请求，用于取访客来源 IP
     * @return SSE 数据流
     * @author Henfon
     * @date 2026-09-21
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> chat(@Valid @RequestBody AiChatRequest request,
                             Authentication authentication,
                             HttpServletRequest servletRequest) {
        AiChatService chatService = chatServiceProvider.getIfAvailable();
        if (chatService == null) {
            // 流已经建立之后再报错会让前端先渲染一个空气泡，这里直接把不可用作为唯一事件返回。
            log.warn("客服对话请求被拒绝：AI 客服未启用");
            return Flux.just("{\"type\":\"error\",\"code\":\"AI_DISABLED\",\"message\":\"在线客服暂未开放\"}");
        }
        Long memberId = memberId(authentication);
        if (!allowed(memberId, servletRequest)) {
            return Flux.just("{\"type\":\"error\",\"code\":\"AI_RATE_LIMITED\",\"message\":\"提问太频繁了，请稍后再试\"}");
        }
        return chatService.chat(AiConversationService.CHANNEL_PORTAL, memberId, request);
    }

    /**
     * 查询会话的聊天记录。
     *
     * @param conversationId 会话标识
     * @param authentication 当前认证信息，访客时为空
     * @return 聊天记录
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<AiChatMessage>> messages(@PathVariable String conversationId,
                                                     Authentication authentication) {
        AiConversation conversation = conversationService.requireAccessible(conversationId,
                AiConversationService.CHANNEL_PORTAL, memberId(authentication));
        List<AiChatMessage> messages = conversationService.messages(conversation.getConversationId()).stream()
                // 工具消息对买家没有意义，且正文为空，过滤掉可以避免前端渲染出空气泡。
                .filter(message -> AiMessage.ROLE_USER.equals(message.getRole())
                        || AiMessage.ROLE_ASSISTANT.equals(message.getRole()))
                .map(AiChatMessage::from)
                .toList();
        return ApiResponse.success(messages, requestId());
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
     * 按调用方维度判断是否还有提问配额。
     *
     * 登录会员按会员 ID 计，访客按来源 IP 计。两者在配额计数器里分属不同键空间，访客伪造不出
     * 会员的额度。
     *
     * @param memberId 当前会员 ID，未登录为空
     * @param request 当前请求
     * @return 是否放行
     * @author Henfon
     * @date 2026-09-21
     */
    private boolean allowed(Long memberId, HttpServletRequest request) {
        if (memberId != null) {
            return rateLimiter.allowMember(memberId);
        }
        return rateLimiter.allowAnonymous(clientIp(request));
    }

    /**
     * 取访客来源 IP，优先使用反向代理透传的 X-Forwarded-For 首个地址。
     *
     * @param request 当前请求
     * @return 来源 IP
     * @author Henfon
     * @date 2026-09-21
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String first = forwarded.split(",")[0].trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        String remoteAddress = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddress) ? remoteAddress : "unknown";
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
