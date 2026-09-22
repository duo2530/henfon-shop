package com.henfon.shop.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.ai.dto.AiAgentEvent;
import com.henfon.shop.ai.dto.AiAgentMessage;
import com.henfon.shop.ai.dto.AiAgentProductDraft;
import com.henfon.shop.ai.dto.AiAgentSessionDetail;
import com.henfon.shop.ai.dto.AiAgentSessionView;
import com.henfon.shop.ai.dto.AiAgentState;
import com.henfon.shop.ai.dto.AiProductCard;
import com.henfon.shop.ai.entity.AiConversation;
import com.henfon.shop.ai.entity.AiMessage;
import com.henfon.shop.ai.mapper.AiMessageMapper;
import com.henfon.shop.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 人工客服接待。
 *
 * 与工单的分工：工单是异步留言，买家提交后离开页面，客服回复时对方多半不在；本服务是
 * 双方都在线的实时对话，买家在客服窗口里等，客服在这里接。两条链路各自独立，不互相转换。
 *
 * 三处刻意的设计：
 *
 * 1. 只有登录会员能进等待队列。访客没有身份，关掉页面客服就找不到人，实时会话会变成
 *    "客服对着空气打字"；而且访客无法做会话归属校验，容易出越权。
 * 2. 消息落库是唯一事实来源，推送只是加速。订阅时先推一份库里的快照，所以断线重连、
 *    刷新页面都不会丢消息，通道本身不需要保证可靠投递。
 * 3. 人工消息以 SENDER_AGENT 落库。它不会被读回模型上下文，买家那边则由前端按发送方
 *    渲染——这是买家能分清"这句话是真人说的"的唯一依据。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Service
public class AiAgentService {

    private static final Logger log = LoggerFactory.getLogger(AiAgentService.class);

    /** 订阅快照与详情返回的消息条数上限，只取最近这些条。 */
    private static final int SNAPSHOT_MESSAGE_LIMIT = 50;

    /** 等待队列与我的会话返回的条数上限。 */
    private static final int SESSION_LIMIT = 50;

    /** 买家单条消息长度上限。 */
    private static final int MAX_CONTENT_LENGTH = 500;

    /** 队列条目里展示买家最近一条消息的长度上限。 */
    private static final int LAST_MESSAGE_LIMIT = 60;

    private final AiConversationService conversationService;

    private final AiMessageMapper messageMapper;

    private final AiConversationChannel channel;

    private final AiAgentStatusService statusService;

    private final AiMemberDirectory memberDirectory;

    private final AiProductCardService productCardService;

    private final AiProductCardReader cardReader;

    private final ObjectMapper objectMapper;

    /**
     * 创建人工接待服务。
     *
     * @param conversationService 会话服务
     * @param messageMapper 消息 Mapper
     * @param channel 会话消息通道
     * @param statusService 坐席状态服务，用于转人工入口的在线校验
     * @param memberDirectory 买家名册，用于把会员 ID 解析成队列上可读的名字
     * @param productCardService 商品卡片装配服务
     * @param cardReader 商品卡片读取器，用于把历史消息里的卡片读回来
     * @param objectMapper JSON 序列化器
     * @author Henfon
     * @date 2026-09-21
     */
    public AiAgentService(AiConversationService conversationService,
                          AiMessageMapper messageMapper,
                          AiConversationChannel channel,
                          AiAgentStatusService statusService,
                          AiMemberDirectory memberDirectory,
                          AiProductCardService productCardService,
                          AiProductCardReader cardReader,
                          ObjectMapper objectMapper) {
        this.conversationService = conversationService;
        this.messageMapper = messageMapper;
        this.channel = channel;
        this.statusService = statusService;
        this.memberDirectory = memberDirectory;
        this.productCardService = productCardService;
        this.cardReader = cardReader;
        this.objectMapper = objectMapper;
    }

    /**
     * 买家请求转人工，进入等待队列。
     *
     * @param memberId 当前会员 ID，未登录由调用方拒绝
     * @param conversationId 会话标识
     * @return 会话视图
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public AiAgentSessionView requestAgent(Long memberId, String conversationId) {
        if (memberId == null) {
            throw new BusinessException("AI_AGENT_LOGIN_REQUIRED", "转人工需要先登录");
        }
        if (StringUtils.hasText(conversationId)) {
            AiConversation current = conversationService.findAccessible(conversationId,
                    AiConversationService.CHANNEL_PORTAL, memberId);
            if (current != null && isAgentHandling(current)) {
                // 已经在人工链路上：排队中的买家重新打开页面再点一次，不该因为此刻没人上线
                // 就被拒之门外，他的位置还在队列里。
                return AiAgentSessionView.from(current);
            }
        }
        // 没有客服能接的时候不让进队列。放进去只会让买家对着一个没人看的队列干等，
        // 明确告诉他"现在没人，先留言"比假装受理更有用。
        statusService.requireAccepting();
        // 会话标识为空表示"一进来就要人工"：新建一条会话并直接进队列，不必先跟机器说一句话。
        AiConversation conversation = StringUtils.hasText(conversationId)
                ? conversationService.requestAgent(conversationId, memberId)
                : conversationService.requestAgentInNewConversation(memberId);
        log.info("会话 {} 由会员 {} 请求转人工，当前接待模式 {}",
                conversation.getConversationId(), memberId, conversation.getServiceMode());
        return AiAgentSessionView.from(conversation);
    }

    /**
     * 买家发送消息。
     *
     * 人工排队中与人工接待中都允许发送：客服还没接入时买家补充说明是常见且合理的行为，
     * 消息先落库，客服接入后从快照里就能看到。
     *
     * @param conversationId 会话标识
     * @param memberId 当前会员 ID
     * @param content 消息正文
     * @return 落库后的消息
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public AiAgentMessage memberSend(String conversationId, Long memberId, String content) {
        String text = requireContent(content);
        AiConversation conversation = requireMemberConversation(conversationId, memberId);
        if (!isAgentHandling(conversation)) {
            // 智能客服接待中应该走提问接口，这里拒绝而不是静默交给模型：两条链路的消息
            // 落库方式不同（一个经 ChatMemory、一个直接写），混着走会把记录写乱。
            throw new BusinessException("AI_AGENT_NOT_IN_SERVICE", "当前由智能客服接待，请直接提问");
        }
        AiMessage record = append(conversationId, AiMessage.ROLE_USER, AiMessage.SENDER_MEMBER, text);
        conversationService.touch(conversationId);
        channel.publish(conversationId, AiAgentEvent.message(conversation, AiAgentMessage.from(record)));
        return AiAgentMessage.from(record);
    }

    /**
     * 买家订阅会话消息流。
     *
     * @param conversationId 会话标识
     * @param memberId 当前会员 ID
     * @return 事件流
     * @author Henfon
     * @date 2026-09-21
     */
    public Flux<AiAgentEvent> subscribeForMember(String conversationId, Long memberId) {
        AiConversation conversation = requireMemberConversation(conversationId, memberId);
        return snapshot(conversation).concatWith(channel.subscribe(conversationId));
    }

    /**
     * 等待人工接入的会话队列。
     *
     * @return 队列，先来先服务
     * @author Henfon
     * @date 2026-09-21
     */
    public List<AiAgentSessionView> waitingQueue() {
        return views(conversationService.waitingQueue(SESSION_LIMIT));
    }

    /**
     * 当前客服正在接待的会话。
     *
     * @param agentId 客服管理员 ID
     * @return 会话列表
     * @author Henfon
     * @date 2026-09-21
     */
    public List<AiAgentSessionView> mySessions(Long agentId) {
        return views(conversationService.servingBy(agentId));
    }

    /**
     * 客服接入会话。
     *
     * 抢单式：谁点谁接，先到先得。并发冲突由会话服务的条件更新兜住。
     *
     * @param conversationId 会话标识
     * @param agentId 客服管理员 ID
     * @return 会话视图
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public AiAgentSessionView join(String conversationId, Long agentId) {
        AiConversation conversation = conversationService.joinAsAgent(conversationId, agentId);
        log.info("客服 {} 接入会话 {}", agentId, conversationId);
        channel.publish(conversationId, AiAgentEvent.state(conversation));
        return view(conversation);
    }

    /**
     * 客服发送回复。
     *
     * @param conversationId 会话标识
     * @param agentId 客服管理员 ID
     * @param content 消息正文
     * @return 落库后的消息
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public AiAgentMessage agentSend(String conversationId, Long agentId, String content) {
        String text = requireContent(content);
        AiConversation conversation = requireServing(conversationId, agentId);
        AiMessage record = append(conversationId, AiMessage.ROLE_ASSISTANT, AiMessage.SENDER_AGENT, text);
        conversationService.touch(conversationId);
        channel.publish(conversationId, AiAgentEvent.message(conversation, AiAgentMessage.from(record)));
        return AiAgentMessage.from(record);
    }

    /**
     * 客服推送商品卡片。
     *
     * 卡片交给服务端按主键查库组装：标题、价格、图片都要以库里的为准，客服手填的图片地址是
     * 对象键，买家打开就是裂图。客服只需在商品主键上加一句说明。
     *
     * 落库时正文进 content、卡片进 tool_payload，与智能客服的卡片走同一套存储约定，于是买家
     * 刷新页面、客服换台电脑重开工作台，读回来的都是同一条卡片消息。
     *
     * @param conversationId 会话标识
     * @param agentId 客服管理员 ID
     * @param draft 商品卡片草稿
     * @return 落库后的消息
     * @author Henfon
     * @date 2026-09-22
     */
    @Transactional
    public AiAgentMessage agentSendProduct(String conversationId, Long agentId, AiAgentProductDraft draft) {
        if (draft == null || draft.productId() == null) {
            throw new BusinessException("AI_PRODUCT_REQUIRED", "请先选择要发送的商品");
        }
        AiConversation conversation = requireServing(conversationId, agentId);
        String note = draft.note() == null ? null : draft.note().trim();
        if (note != null && note.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("AI_MESSAGE_TOO_LONG", "说明过长，请精简后发送");
        }
        List<AiProductCard> cards = productCardService.cards(List.of(draft.productId()), note);
        if (cards.isEmpty()) {
            // 商品没了或已下架。静默发一条空消息会让客服以为发出去了，明确报错更省事。
            throw new BusinessException("AI_PRODUCT_NOT_FOUND", "商品不存在或已下架");
        }
        AiProductCard card = cards.get(0);
        String content = StringUtils.hasText(note) ? note : "为您推荐：" + card.title();
        AiMessage record = append(conversationId, AiMessage.ROLE_ASSISTANT, AiMessage.SENDER_AGENT, content,
                cardPayload(cards));
        conversationService.touch(conversationId);
        AiAgentMessage view = AiAgentMessage.withCards(record, cards);
        channel.publish(conversationId, AiAgentEvent.product(conversation, view, draft));
        return view;
    }

    /**
     * 客服结束人工接待，会话退回智能客服。
     *
     * 结束事件单独推给买家：门户要凭它弹评价，而 state 只是"谁在接待"的变化，买家侧还要
     * 另外判断这次变化是不是"从人工退回 AI"，判断散在前端迟早会漏掉某个分支。
     *
     * @param conversationId 会话标识
     * @param agentId 客服管理员 ID
     * @return 会话视图
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public AiAgentSessionView end(String conversationId, Long agentId) {
        AiConversation conversation = conversationService.endAgentService(conversationId, agentId);
        log.info("客服 {} 结束会话 {}", agentId, conversationId);
        channel.publish(conversationId, AiAgentEvent.ended(conversation));
        return view(conversation);
    }

    /**
     * 买家主动结束本次人工咨询。
     *
     * 客服忘点结束时买家不该被困住：会话挂在 HUMAN 上，提问被拦、智能客服也用不了。这条路径
     * 不校验"是不是接管的客服"（买家本来就是另一方），但校验归属，且只允许结束接待中的会话。
     *
     * 推 ended 而不只是 state：买家侧凭 ended 把窗口交回智能客服并弹出评价，与客服主动结束
     * 走同一条收尾逻辑，不必为"谁结束的"分两套。
     *
     * 排队中的会话也允许结束：等了几十分钟没人接，买家想撤回去继续问智能客服是合理需求。
     *
     * @param conversationId 会话标识
     * @param memberId 当前会员 ID
     * @return 会话视图
     * @author Henfon
     * @date 2026-09-22
     */
    @Transactional
    public AiAgentSessionView memberEnd(String conversationId, Long memberId) {
        AiConversation conversation = requireMemberConversation(conversationId, memberId);
        String mode = conversation.getServiceMode();
        if (!AiConversationService.SERVICE_HUMAN.equals(mode)
                && !AiConversationService.SERVICE_WAITING.equals(mode)) {
            // 已经是 AI 接待，重复调用按幂等处理：返回当前状态比抛错更贴合"我想回到智能客服"的意图。
            return view(conversation);
        }
        AiConversation ended = conversationService.endByMember(conversationId);
        if (ended == null) {
            AiConversation latest = conversationService.findByConversationId(conversationId);
            return latest == null ? view(conversation) : view(latest);
        }
        log.info("买家结束会话 {}，原接待模式 {}", conversationId, mode);
        // 排队中取消不该弹评价：这次压根没有客服接待过，评价服务会以"没有人工接待"拒绝打分。
        if (AiConversationService.SERVICE_HUMAN.equals(mode)) {
            channel.publish(conversationId, AiAgentEvent.ended(ended));
        } else {
            channel.publish(conversationId, AiAgentEvent.state(ended));
        }
        return view(ended);
    }

    /**
     * 客服订阅会话消息流，需已接管该会话。
     *
     * 未接管的会话不开放订阅：等待队列里已经给了买家的问题原文，足够判断要不要接；
     * 放开订阅等于让任何客服都能围观任意买家的完整对话。
     *
     * @param conversationId 会话标识
     * @param agentId 客服管理员 ID
     * @return 事件流
     * @author Henfon
     * @date 2026-09-21
     */
    public Flux<AiAgentEvent> subscribeForAgent(String conversationId, Long agentId) {
        AiConversation conversation = requireServing(conversationId, agentId);
        return snapshot(conversation).concatWith(channel.subscribe(conversationId));
    }

    /**
     * 会话详情，含最近消息。
     *
     * @param conversationId 会话标识
     * @param agentId 客服管理员 ID
     * @return 详情
     * @author Henfon
     * @date 2026-09-21
     */
    public AiAgentSessionDetail detail(String conversationId, Long agentId) {
        AiConversation conversation = requireServing(conversationId, agentId);
        return new AiAgentSessionDetail(conversation.getConversationId(), conversation.getMemberId(),
                conversation.getTitle(), conversation.getServiceMode(), conversation.getAgentId(),
                recentMessages(conversationId));
    }

    /**
     * 买家侧查询会话接待状态。
     *
     * 买家刷新页面后用它恢复上一次的会话：既要知道现在是不是还有客服在接待，也要拿到
     * 会话标识继续收发消息，否则每刷新一次就得重新描述一遍问题。
     *
     * @param conversationId 会话标识
     * @param memberId 当前会员 ID
     * @return 接待状态
     * @author Henfon
     * @date 2026-09-21
     */
    public AiAgentState state(String conversationId, Long memberId) {
        AiConversation conversation = requireMemberConversation(conversationId, memberId);
        return AiAgentState.from(conversation);
    }

    /**
     * 读取会话并要求已登录会员，是点对点的人工会话接口的统一入口。
     *
     * 单独卡一次登录，而不是只依赖会话服务的归属校验：匿名发起的会话没有归属，那条路径会
     * 放行——而人工会话的接口本来就不该对访客开放，等到将来有人给访客开一个转人工入口时，
     * 这层判断就是唯一的拦截点。
     *
     * @param conversationId 会话标识
     * @param memberId 当前会员 ID
     * @return 会话
     * @author Henfon
     * @date 2026-09-21
     */
    private AiConversation requireMemberConversation(String conversationId, Long memberId) {
        if (memberId == null) {
            throw new BusinessException("AI_AGENT_LOGIN_REQUIRED", "转人工需要先登录");
        }
        return conversationService.requireAccessible(conversationId, AiConversationService.CHANNEL_PORTAL, memberId);
    }

    /**
     * 读取会话并确认当前客服正在接待它。
     *
     * @param conversationId 会话标识
     * @param agentId 客服管理员 ID
     * @return 会话
     * @author Henfon
     * @date 2026-09-21
     */
    private AiConversation requireServing(String conversationId, Long agentId) {
        if (agentId == null) {
            throw new BusinessException("AI_AGENT_LOGIN_REQUIRED", "请先登录");
        }
        AiConversation conversation = conversationService.findByConversationId(conversationId);
        if (conversation == null) {
            throw new BusinessException("AI_CONVERSATION_NOT_FOUND", "会话不存在或已过期");
        }
        if (!AiConversationService.SERVICE_HUMAN.equals(conversation.getServiceMode())) {
            throw new BusinessException("AI_AGENT_NOT_SERVING", "该会话当前不是人工接待中");
        }
        if (!agentId.equals(conversation.getAgentId())) {
            throw new BusinessException("AI_AGENT_FORBIDDEN", "该会话由其他客服接待");
        }
        return conversation;
    }

    /**
     * 判断会话是否在人工链路上。
     *
     * @param conversation 会话
     * @return 排队中或人工接待中返回 true
     * @author Henfon
     * @date 2026-09-21
     */
    private boolean isAgentHandling(AiConversation conversation) {
        String mode = conversation.getServiceMode();
        return AiConversationService.SERVICE_WAITING.equals(mode)
                || AiConversationService.SERVICE_HUMAN.equals(mode);
    }

    /**
     * 构建订阅快照。
     *
     * @param conversation 会话
     * @return 单元素事件流
     * @author Henfon
     * @date 2026-09-21
     */
    private Flux<AiAgentEvent> snapshot(AiConversation conversation) {
        List<AiAgentMessage> messages = recentMessages(conversation.getConversationId());
        return Flux.just(AiAgentEvent.snapshot(conversation, messages));
    }

    /**
     * 构建带买家身份的会话视图。
     *
     * @param conversation 会话
     * @return 视图
     * @author Henfon
     * @date 2026-09-21
     */
    private AiAgentSessionView view(AiConversation conversation) {
        AiMemberDirectory.MemberBadge badge = memberDirectory.badge(conversation.getMemberId());
        return AiAgentSessionView.from(conversation,
                badge == null ? null : badge.name(),
                badge == null ? null : badge.avatarUrl(),
                lastMemberMessage(conversation.getConversationId()));
    }

    /**
     * 批量构建带买家身份的会话视图。
     *
     * 买家名与头像一次查完；最近一条买家消息按会话逐条取，一次 LIMIT 1 走的是会话索引，队列为空时
     * 一次查询也不发——比把整段聊天记录读回来再在内存里挑最后一条便宜得多。
     *
     * @param conversations 会话列表
     * @return 视图列表
     * @author Henfon
     * @date 2026-09-21
     */
    private List<AiAgentSessionView> views(List<AiConversation> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return List.of();
        }
        Map<Long, AiMemberDirectory.MemberBadge> badges = memberDirectory.badges(
                conversations.stream().map(AiConversation::getMemberId).toList());
        return conversations.stream()
                .map(conversation -> {
                    AiMemberDirectory.MemberBadge badge = conversation.getMemberId() == null
                            ? null
                            : badges.get(conversation.getMemberId());
                    return AiAgentSessionView.from(conversation,
                            badge == null ? null : badge.name(),
                            badge == null ? null : badge.avatarUrl(),
                            lastMemberMessage(conversation.getConversationId()));
                })
                .toList();
    }

    /**
     * 读取买家在会话里最近说的一句话。
     *
     * @param conversationId 会话标识
     * @return 单行文本，没有买家消息时返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    private String lastMemberMessage(String conversationId) {
        AiMessage last = messageMapper.selectOne(Wrappers.lambdaQuery(AiMessage.class)
                .eq(AiMessage::getConversationId, conversationId)
                .eq(AiMessage::getRole, AiMessage.ROLE_USER)
                .orderByDesc(AiMessage::getSequence)
                .last("LIMIT 1"));
        if (last == null || !StringUtils.hasText(last.getContent())) {
            return null;
        }
        String text = last.getContent().trim().replaceAll("\\s+", " ");
        return text.length() <= LAST_MESSAGE_LIMIT ? text : text.substring(0, LAST_MESSAGE_LIMIT) + "…";
    }

    /**
     * 读取会话最近的消息，工具与系统消息不返回。
     *
     * @param conversationId 会话标识
     * @return 消息列表，按序号升序
     * @author Henfon
     * @date 2026-09-21
     */
    private List<AiAgentMessage> recentMessages(String conversationId) {
        List<AiMessage> records = messageMapper.selectList(Wrappers.lambdaQuery(AiMessage.class)
                .eq(AiMessage::getConversationId, conversationId)
                .orderByDesc(AiMessage::getSequence)
                .last("LIMIT " + SNAPSHOT_MESSAGE_LIMIT));
        // 倒序取最近若干条，展示时再翻回正序。
        return records.stream()
                .filter(AiAgentMessage::visible)
                .sorted(java.util.Comparator.comparing(AiMessage::getSequence))
                .map(record -> AiAgentMessage.from(record, cardReader::read))
                .toList();
    }

    /**
     * 追加一条消息。
     *
     * 会话内序号由"当前最大值加一"得到，与 ChatMemory 写入共用同一套序号。并发下同一个
     * 序号可能被两方同时选中，唯一索引会拒绝后到的那条，这里重试即可——聊天消息的频率
     * 远低于冲突概率，为它上分布式锁不划算。
     *
     * @param conversationId 会话标识
     * @param role 消息角色
     * @param sender 发送方
     * @param content 正文
     * @return 落库后的记录
     * @author Henfon
     * @date 2026-09-21
     */
    private AiMessage append(String conversationId, String role, String sender, String content) {
        return append(conversationId, role, sender, content, null);
    }

    /**
     * 追加一条消息，并带上快照。
     *
     * 会话内序号由"当前最大值加一"得到，与 ChatMemory 写入共用同一套序号。并发下同一个
     * 序号可能被两方同时选中，唯一索引会拒绝后到的那条，这里重试即可——聊天消息的频率
     * 远低于冲突概率，为它上分布式锁不划算。
     *
     * @param conversationId 会话标识
     * @param role 消息角色
     * @param sender 发送方
     * @param content 正文
     * @param toolPayload 快照 JSON，没有则传空
     * @return 落库后的记录
     * @author Henfon
     * @date 2026-09-22
     */
    private AiMessage append(String conversationId, String role, String sender, String content, String toolPayload) {
        for (int attempt = 0; attempt < 3; attempt++) {
            AiMessage last = messageMapper.selectOne(Wrappers.lambdaQuery(AiMessage.class)
                    .eq(AiMessage::getConversationId, conversationId)
                    .orderByDesc(AiMessage::getSequence)
                    .last("LIMIT 1"));
            int sequence = last == null || last.getSequence() == null ? 1 : last.getSequence() + 1;
            AiMessage record = new AiMessage();
            record.setConversationId(conversationId);
            record.setSequence(sequence);
            record.setRole(role);
            record.setSender(sender);
            record.setContent(content);
            record.setToolPayload(toolPayload);
            record.setCreatedAt(LocalDateTime.now());
            try {
                messageMapper.insert(record);
                return record;
            } catch (DuplicateKeyException exception) {
                log.debug("会话 {} 的消息序号 {} 已被占用，重试第 {} 次", conversationId, sequence, attempt + 1);
            }
        }
        throw new BusinessException("AI_MESSAGE_CONFLICT", "消息发送失败，请重试");
    }

    /**
     * 把商品卡片序列化成消息快照。
     *
     * 格式与智能客服写入的快照保持一致（{@code {"cards":[...]}}），读路径才能用同一段代码
     * 把两边写的卡片都解出来。序列化失败返回空：卡片发不出去不该连正文一起丢掉。
     *
     * @param cards 商品卡片
     * @return JSON 文本，失败时返回 null
     * @author Henfon
     * @date 2026-09-22
     */
    private String cardPayload(List<AiProductCard> cards) {
        try {
            return objectMapper.writeValueAsString(Map.of("cards", cards));
        } catch (Exception exception) {
            log.warn("序列化商品卡片失败：{}", exception.getMessage());
            return null;
        }
    }

    /**
     * 校验并规整消息正文。
     *
     * @param content 原始正文
     * @return 规整后的正文
     * @author Henfon
     * @date 2026-09-21
     */
    private String requireContent(String content) {
        String text = content == null ? "" : content.trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("AI_MESSAGE_EMPTY", "消息内容不能为空");
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("AI_MESSAGE_TOO_LONG", "消息过长，请精简后发送");
        }
        return text;
    }
}
