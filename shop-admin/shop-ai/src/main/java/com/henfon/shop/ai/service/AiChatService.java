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
import com.henfon.shop.ai.tool.AiProductToolRecorder;
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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final ChatClient aiChatClient;

    private final AiKnowledgeRetriever retriever;

    private final AiConversationService conversationService;

    private final AiPortalTools portalTools;

    private final AiProductTools productTools;

    /**
     * 商品工具的方法级回调，构造时解析一次。
     *
     * 每轮提问要的是「这些回调 + 本轮的收集器」，所以这里只留签名与描述那部分不变的东西，
     * 收集器在包装时按轮传入。
     */
    private final ToolCallback[] productToolCallbacks;

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
        this.productToolCallbacks = MethodToolCallbackProvider.builder()
                .toolObjects(productTools)
                .build()
                .getToolCallbacks();
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
        // 这里必须是 toolCallbacks：tools() 只认带 @Tool 注解的对象，传回调进去会被当成
        // 待扫描的对象，运行期抛「没有找到 @Tool 方法」。
        spec = spec.toolCallbacks(recordingProductTools(state));
        if (memberId != null) {
            // 身份在这里进工具上下文，工具签名里不出现它，模型也就改不了它。
            // 未登录时干脆不挂个人查询工具：模型看不到有哪些查询手段，就没有东西可以拿来讲。
            // 商品工具的签名里不带 ToolContext，所以未登录时不给上下文也不会有问题。
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
        return Flux.just(AiChatEvent.meta(conversationId, state.refs(), state.hit()))
                .concatWith(deltas)
                // 先卡"多久没有新内容"，再卡"整轮最长多久"：前者对应模型侧无响应，后者对应
                // 持续吐字但总也说不完，两种情况对用户与排查的含义不同。
                .timeout(Duration.ofMillis(Math.max(1000, firstTokenTimeout)))
                .concatWith(Flux.defer(() -> Flux.just(
                        AiChatEvent.done(conversationId, state.finishReason(), drainCards(state)))))
                .timeout(Duration.ofMillis(Math.max(1000, streamTimeout)))
                .map(this::toJson)
                .onErrorResume(error -> Flux.just(errorEvent(error)))
                .doFinally(signal -> persist(conversationId, state));
    }

    /**
     * 把商品工具包一层，让这一轮能收到工具返回里的商品编号。
     *
     * 包装按轮新建，收集器也就跟着一轮一个，不需要线程局部变量，也不会串到别的买家身上。
     *
     * @param state 本轮累积状态
     * @return 可直接交给对话客户端的工具回调
     * @author Henfon
     * @date 2026-09-22
     */
    private ToolCallback[] recordingProductTools(StreamState state) {
        List<ToolCallback> wrapped = new ArrayList<>(productToolCallbacks.length);
        for (ToolCallback callback : productToolCallbacks) {
            wrapped.add(new AiProductToolRecorder(callback, state::recordProductIds));
        }
        return wrapped.toArray(new ToolCallback[0]);
    }

    /**
     * 取出本轮要下发的商品卡片。
     *
     * @param state 本轮累积状态
     * @return 商品卡片，没有检索结果时返回空列表
     * @author Henfon
     * @date 2026-09-22
     */
    private List<AiProductCard> drainCards(StreamState state) {
        List<Long> productIds = cardCandidateIds(state);
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
     * 取本轮要出卡片的商品主键，两个来源合并。
     *
     * <p>检索命中的 PRODUCT 引用：回答本身就是基于这批引用生成的，用同一批主键出卡片，推荐与
     * 卡片必然一致。只认工具回调是不够的——提问前的那次检索已经把商品塞进上下文，模型拿到
     * 现成清单后通常不会再调工具，落库快照里只有 refs、没有 toolCalls。</p>
     *
     * <p>商品检索工具的返回：模型判断「资料里没有、再搜一次」时主动调工具，那一批商品不在引用
     * 里，但同样是它作答的依据，不并进来就会漏卡片。编号由 {@link AiProductToolRecorder} 在
     * 工具返回时记下。</p>
     *
     * <p>工具在前、引用在后：卡片一次只出三张，顺序就是取舍。模型主动调了工具，说明它认为
     * 手上的资料不够、要按当前提问实时查一遍，那批结果比相似度召回的引用更贴近问题；把引用
     * 排前面，工具查到的商品会永远挤不进前三，等于这一路白接。重复的主键只算一次。</p>
     *
     * @param state 本轮累积状态
     * @return 商品主键，已去重
     * @author Henfon
     * @date 2026-09-22
     */
    private List<Long> cardCandidateIds(StreamState state) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>(state.toolProductIds());
        for (Ref ref : state.refs) {
            if (!Ref.SOURCE_PRODUCT.equals(ref.sourceType())) {
                continue;
            }
            try {
                ids.add(Long.valueOf(ref.sourceId()));
            } catch (NumberFormatException exception) {
                // 非数字的来源标识不是商品主键，跳过即可，不值得为它中断整轮回答
                log.debug("引用来源标识不是商品主键，忽略：{}", ref.sourceId());
            }
        }
        ids.addAll(state.toolProductIds());
        return List.copyOf(ids);
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

        /**
         * 本轮商品检索工具返回里的商品主键。
         *
         * 工具在框架内部的线程上执行，与本轮累积文本的不是同一条，所以读写都过同一把锁；
         * 一轮之内只写几次、末尾读一次，锁的开销可以忽略。
         */
        private final Set<Long> toolProductIds = new LinkedHashSet<>();

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
         * 记下商品检索工具返回的商品主键。
         *
         * @param ids 商品主键
         * @author Henfon
         * @date 2026-09-22
         */
        synchronized void recordProductIds(Collection<Long> ids) {
            if (ids != null) {
                toolProductIds.addAll(ids);
            }
        }

        /**
         * 读出本轮工具返回的商品主键。
         *
         * @return 商品主键，按工具返回顺序
         * @author Henfon
         * @date 2026-09-22
         */
        synchronized List<Long> toolProductIds() {
            return List.copyOf(toolProductIds);
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
