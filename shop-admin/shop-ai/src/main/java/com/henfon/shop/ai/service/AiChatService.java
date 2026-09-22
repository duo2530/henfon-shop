package com.henfon.shop.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.ai.chat.AiPrompts;
import com.henfon.shop.ai.config.AiProperties;
import com.henfon.shop.ai.dto.AiChatEvent;
import com.henfon.shop.ai.dto.AiChatRequest;
import com.henfon.shop.ai.dto.AiProductCard;
import com.henfon.shop.ai.dto.Ref;
import com.henfon.shop.ai.entity.AiConversation;
import com.henfon.shop.ai.retrieval.AiKnowledgeRetriever;
import com.henfon.shop.ai.tool.AiPortalTools;
import com.henfon.shop.ai.tool.AiProductTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 客服对话编排。
 *
 * 一轮请求的顺序：取会话 → 检索知识 → 拼系统提示 → 流式生成 → 回填用量与引用。
 *
 * 几处刻意的设计：
 *
 * 1. 系统提示按请求重建，知识片段只存在于本轮请求里。会话记忆不保存系统消息（框架的消息
 *    窗口顾问在写入侧只落用户消息与助手回复），所以上一轮检索到的资料不会在下一轮被当成
 *    "用户说过的话"重复送进模型。
 * 2. 检索未命中时不阻断生成，而是把"本次没有检索到资料"写进系统提示。原因是问候语、致谢
 *    这类对话本来就不该有条目，直接返回固定话术会把正常寒暄也怼回去；真正的约束落在提示词
 *    第一条——没有资料就不许给业务结论。
 * 3. 生成失败、超时、向量库不可达都转换成流里的 error 事件，HTTP 状态保持 200。SSE 已经
 *    开始推流之后再改状态码没有意义，前端拿不到，能拿到的是事件。
 * 4. 落库放在流的终止回调里，包括客户端主动断开的情况：用户关掉客服窗口时，已经说出去的
 *    半截回答仍然是这次对话的记录，丢掉会让运营看到的会话缺一段。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Service
@ConditionalOnProperty(prefix = "shop.ai", name = "enabled", havingValue = "true")
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    /** 本轮商品检索的候选数量上限，与卡片上限分开：模型可以多看到几款，卡片只出最靠前的几张。 */
    private static final int AI_CARD_CANDIDATES = 6;

    private final ChatClient aiChatClient;

    private final AiKnowledgeRetriever retriever;

    private final AiConversationService conversationService;

    private final AiPortalTools portalTools;

    private final AiProductTools productTools;

    private final AiProductCardService productCardService;

    private final AiProperties properties;

    private final ObjectMapper objectMapper;

    /**
     * 创建对话编排服务。
     *
     * @param aiChatClient 客服对话客户端
     * @param retriever 知识库检索组件
     * @param conversationService 会话管理服务
     * @param portalTools 买家侧查询工具，订单与物流走它
     * @param productTools 商品检索工具，买家问「帮我找 XX」时走它
     * @param productCardService 商品卡片装配服务
     * @param properties AI 配置
     * @param objectMapper JSON 序列化器
     * @author Henfon
     * @date 2026-09-21
     */
    public AiChatService(ChatClient aiChatClient,
                         AiKnowledgeRetriever retriever,
                         AiConversationService conversationService,
                         AiPortalTools portalTools,
                         AiProductTools productTools,
                         AiProductCardService productCardService,
                         AiProperties properties,
                         ObjectMapper objectMapper) {
        this.aiChatClient = aiChatClient;
        this.retriever = retriever;
        this.conversationService = conversationService;
        this.portalTools = portalTools;
        this.productTools = productTools;
        this.productCardService = productCardService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理一轮提问，返回 SSE 数据流。
     *
     * 会话创建与知识检索在返回 Flux 之前完成，这样"会话不存在、会话不属于当前账号"这类
     * 错误会以业务异常冒泡到统一异常处理器，走正常的 HTTP 错误响应；等 SSE 建立之后再往流里
     * 塞错误事件，前端就得维护两套错误处理。
     *
     * @param channel 入口渠道
     * @param memberId 当前会员 ID，未登录为空
     * @param request 提问请求
     * @return SSE 数据流，每项是一段 JSON
     * @author Henfon
     * @date 2026-09-21
     */
    public Flux<String> chat(String channel, Long memberId, AiChatRequest request) {
        AiConversation conversation = conversationService.getOrCreate(request.conversationId(), channel, memberId,
                request.question(), request.subjectType(), request.subjectId());
        String conversationId = conversation.getConversationId();
        String serviceMode = conversation.getServiceMode();
        if (AiConversationService.SERVICE_WAITING.equals(serviceMode)
                || AiConversationService.SERVICE_HUMAN.equals(serviceMode)) {
            // 人工链路已经接管这条会话时不能再由模型作答：买家正在跟真人对话，机器插一句会把
            // 两个声音混在一起，而人工回复又不进模型上下文（读取侧按 sender 过滤），模型给出的
            // 结论与客服刚说的话可能直接冲突。前端收到这个码就停在人工会话里，不再提问。
            log.info("会话 {} 当前为 {}，拒绝智能客服作答", conversationId, serviceMode);
            return Flux.just(toJson(AiChatEvent.error("AI_AGENT_SERVING", "当前由人工客服接待，请直接在对话里回复")));
        }

        AiKnowledgeRetriever.RetrievalResult retrieval = retriever.retrieve(request.question(), null);
        String systemPrompt = AiPrompts.portalSystem(retrieval.context(), memberId != null);
        StreamState state = new StreamState(retrieval.hit(), retrieval.toRefs());

        ChatClient.ChatClientRequestSpec spec = aiChatClient.prompt()
                .system(systemPrompt)
                .user(request.question());
        // 商品检索对未登录买家也开放（在售商品是公开信息），订单类工具不行：先挂公用的那个。
        spec = spec.tools(productTools);
        if (memberId != null) {
            // 身份在这里进工具上下文，工具签名里不出现它，模型也就改不了它。
            // 未登录时干脆不挂个人查询工具：模型看不到有哪些查询手段，就没有东西可以拿来讲。
            spec = spec.tools(portalTools)
                    .toolContext(Map.<String, Object>of(AiPortalTools.CONTEXT_MEMBER_ID, memberId));
        }

        Flux<AiChatEvent> deltas = spec
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .chatClientResponse()
                // 这里必须是 handle，不能写成 map(state::consume).filter(Objects::nonNull)：
                // 模型最后一片只带用量与结束原因、没有正文，consume 会返回 null，而 Reactor 的
                // map 不允许映射出 null，会抛 "The mapper returned a null value" 打断整条流——
                // 用户看到的是完整回答后面跟一条"服务不可用"，done 事件永远发不出去。
                .handle((response, sink) -> {
                    AiChatEvent event = state.consume(response);
                    if (event != null) {
                        sink.next(event);
                    }
                });

        long firstTokenTimeout = properties.getChat().getFirstTokenTimeoutMs();
        long streamTimeout = properties.getChat().getStreamTimeoutMs();
        // 检索状态挂在调用线程上，工具回调回来时把命中的商品主键记在里面，收尾时统一取卡片。
        AiProductTools.ProductSearchState.begin(AI_CARD_CANDIDATES);
        return Flux.just(AiChatEvent.meta(conversationId, state.refs(), state.hit()))
                .concatWith(deltas)
                // 先卡"多久没有新内容"，再卡"整轮最长多久"：前者对应模型侧无响应，后者对应
                // 持续吐字但总也说不完，两种情况对用户与排查的含义不同。
                .timeout(Duration.ofMillis(Math.max(1000, firstTokenTimeout)))
                .concatWith(Flux.defer(() -> Flux.just(
                        AiChatEvent.done(conversationId, state.finishReason(), drainCards(state)))))
                .timeout(Duration.ofMillis(Math.max(1000, streamTimeout)))
                .map(this::toJson)
                .onErrorResume(error -> {
                    // 出错时这一轮不会有卡片下发，检索状态必须就地丢掉：它与线程绑定，留着就会
                    // 污染这个线程上的下一次请求，让另一个买家的回答里冒出上一位搜过的商品。
                    AiProductTools.ProductSearchState.discard();
                    return Flux.just(errorEvent(error));
                })
                .doFinally(signal -> persist(conversationId, state));
    }

    /**
     * 取出本轮检索命中的商品卡片，并清掉线程上的检索状态。
     *
     * 无论这一轮有没有检索到商品都必须取一次：状态是 ThreadLocal，不清掉会影响这个线程上的
     * 下一次请求——买家换个人问，卡片会带着上一位买家的候选。异常收尾走 discard 那条路，
     * 这里只负责正常结束的那一次。
     *
     * @param state 本轮累积状态
     * @return 商品卡片，没有检索结果时返回空列表
     * @author Henfon
     * @date 2026-09-22
     */
    private List<AiProductCard> drainCards(StreamState state) {
        List<Long> productIds = AiProductTools.ProductSearchState.finish();
        if (productIds.isEmpty()) {
            return List.of();
        }
        try {
            List<AiProductCard> cards = productCardService.cards(productIds, null);
            state.cards = cards;
            return cards;
        } catch (Exception exception) {
            log.warn("装配商品卡片失败：{}", exception.getMessage());
            return List.of();
        }
    }

    /**
     * 把事件序列化成 JSON。
     *
     * @param event 事件
     * @return JSON 文本
     * @author Henfon
     * @date 2026-09-21
     */
    private String toJson(AiChatEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception exception) {
            log.error("序列化对话事件失败：{}", exception.getMessage());
            return "{\"type\":\"error\",\"message\":\"服务异常\"}";
        }
    }

    /**
     * 生成异常事件。
     *
     * @param error 异常
     * @return JSON 文本
     * @author Henfon
     * @date 2026-09-21
     */
    private String errorEvent(Throwable error) {
        log.warn("对话流式生成失败：{}", error.getMessage());
        String code = error instanceof TimeoutException ? "AI_TIMEOUT" : "AI_UNAVAILABLE";
        return toJson(AiChatEvent.error(code, describe(error)));
    }

    /**
     * 把异常转换成给用户看的说明。
     *
     * 不回传异常原文：模型服务的报错里会带请求体片段与内部地址，客服窗口不需要这些。
     *
     * @param error 异常
     * @return 说明文本
     * @author Henfon
     * @date 2026-09-21
     */
    private String describe(Throwable error) {
        if (error instanceof TimeoutException) {
            return "回答超时了，请再说一次或者留言给人工客服";
        }
        return "客服暂时不可用，请稍后再试或者留言给人工客服";
    }

    /**
     * 落库本轮结果。
     *
     * 消息本身由会话记忆的存储侧写入，这里只补两样它拿不到的东西：会话的消息计数与最近消息
     * 时间、助手消息的模型与用量。整个方法不能抛异常——它跑在流的终止回调里，异常会被 Reactor
     * 丢弃并留下一条不完整的告警，反而掩盖了真正的失败原因。
     *
     * @param conversationId 会话标识
     * @param state 本轮累积状态
     * @author Henfon
     * @date 2026-09-21
     */
    private void persist(String conversationId, StreamState state) {
        try {
            conversationService.touch(conversationId);
            conversationService.fillLastAssistantMetrics(conversationId, state.model(), state.tokensIn(),
                    state.tokensOut(), state.latencyMs(), toolPayload(state.refs(), state.cards));
        } catch (Exception exception) {
            log.warn("回写会话 {} 的统计信息失败：{}", conversationId, exception.getMessage());
        }
    }

    /**
     * 把检索引用与商品卡片序列化成快照。
     *
     * 商品卡片必须落库：买家在人工接待结束后、或刷新页面时靠历史接口把记录读回来，卡片只在
     * 流里下发一次的话，这段对话就只剩一句"我给你找了几款"，商品全没了。落的是卡片快照而不是
     * 商品主键——卡片本身就是给买家看的那份数据，回读时不必再查一次商品表，也就不会读到
     * 与当时不同的价格。
     *
     * @param refs 引用列表
     * @param cards 商品卡片
     * @return JSON 文本，两者都为空时返回 null
     * @author Henfon
     * @date 2026-09-22
     */
    private String toolPayload(List<Ref> refs, List<AiProductCard> cards) {
        boolean hasRefs = refs != null && !refs.isEmpty();
        boolean hasCards = cards != null && !cards.isEmpty();
        if (!hasRefs && !hasCards) {
            return null;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (hasRefs) {
                payload.put("refs", refs);
            }
            if (hasCards) {
                payload.put("cards", cards);
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            log.warn("序列化引用与商品卡片快照失败：{}", exception.getMessage());
            return null;
        }
    }

    /**
     * 单轮对话的累积状态。
     *
     * 一个实例只服务一条流：Reactor 的序列在单个订阅里是顺序的，这样比把状态塞进上下文或
     * 原子容器更容易读。
     *
     * @author Henfon
     * @date 2026-09-21
     */
    private static final class StreamState {

        /** 知识库是否命中，前端据此决定要不要给"转人工"入口。 */
        private final boolean hit;

        /** 本轮引用到的知识来源。 */
        private final List<Ref> refs;

        /** 本轮下发给买家的商品卡片，落库时一并写进消息快照。 */
        private List<AiProductCard> cards = List.of();

        private final long startedAt = System.currentTimeMillis();

        private Integer tokensIn;

        private Integer tokensOut;

        private String model;

        /** 结束原因，默认按正常结束处理，模型未给出时不该把回答标成异常。 */
        private String finishReason = "STOP";

        private StreamState(boolean hit, List<Ref> refs) {
            this.hit = hit;
            this.refs = refs;
        }

        /**
         * 读取一片流式响应，累积文本与用量。
         *
         * @param response 框架返回的流式响应
         * @return 增量文本事件，本片没有正文时返回 null
         * @author Henfon
         * @date 2026-09-21
         */
        private AiChatEvent consume(ChatClientResponse response) {
            ChatResponse chatResponse = response.chatResponse();
            if (chatResponse == null) {
                return null;
            }
            readMetadata(chatResponse.getMetadata());
            StringBuilder text = new StringBuilder();
            for (Generation generation : chatResponse.getResults()) {
                readFinishReason(generation.getMetadata());
                AssistantMessage output = generation.getOutput();
                if (output != null && StringUtils.hasText(output.getText())) {
                    text.append(output.getText());
                }
            }
            return text.isEmpty() ? null : AiChatEvent.delta(text.toString());
        }

        /**
         * 累积模型名与 token 用量。
         *
         * DashScope 的流式响应只在最后一片带上用量，中途没有是正常的，所以按"有则覆盖"处理，
         * 而不是每片都清零。
         *
         * @param metadata 响应元数据
         * @author Henfon
         * @date 2026-09-21
         */
        private void readMetadata(ChatResponseMetadata metadata) {
            if (metadata == null) {
                return;
            }
            if (StringUtils.hasText(metadata.getModel())) {
                model = metadata.getModel();
            }
            Usage usage = metadata.getUsage();
            if (usage == null) {
                return;
            }
            if (usage.getPromptTokens() != null) {
                tokensIn = usage.getPromptTokens();
            }
            if (usage.getCompletionTokens() != null) {
                tokensOut = usage.getCompletionTokens();
            }
        }

        /**
         * 记录结束原因。
         *
         * @param metadata 生成结果元数据
         * @author Henfon
         * @date 2026-09-21
         */
        private void readFinishReason(ChatGenerationMetadata metadata) {
            if (metadata != null && StringUtils.hasText(metadata.getFinishReason())) {
                finishReason = metadata.getFinishReason();
            }
        }

        private boolean hit() {
            return hit;
        }

        private List<Ref> refs() {
            return refs;
        }

        private String model() {
            return model;
        }

        private Integer tokensIn() {
            return tokensIn;
        }

        private Integer tokensOut() {
            return tokensOut;
        }

        private String finishReason() {
            return finishReason;
        }

        private Integer latencyMs() {
            return (int) (System.currentTimeMillis() - startedAt);
        }
    }
}
