package com.henfon.shop.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.henfon.shop.ai.entity.AiConversation;
import com.henfon.shop.ai.entity.AiMessage;
import com.henfon.shop.ai.mapper.AiConversationMapper;
import com.henfon.shop.ai.mapper.AiMessageMapper;
import com.henfon.shop.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会话管理。
 *
 * 会话主表与消息明细分开维护：消息由 ChatMemory 的存储侧写入，本类只负责会话本身
 * （归属、标题、状态、消息计数）。消息计数不靠累加，每轮结束后按 ai_message 实际条数
 * 回写——累加在多加一次、少加一次的边界上很容易和真实条数漂移，而计数只是列表展示用，
 * 一次 count 查询换掉一类"数据对不上"的排查成本是划算的。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Service
public class AiConversationService {

    private static final Logger log = LoggerFactory.getLogger(AiConversationService.class);

    /** 门户买家入口。 */
    public static final String CHANNEL_PORTAL = "PORTAL";

    /** 管理端运营助手入口。 */
    public static final String CHANNEL_ADMIN = "ADMIN";

    /** 会话进行中。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 已提交工单。转人工走 serviceMode，不再用这个状态表达。 */
    public static final String STATUS_TICKETED = "TICKETED";

    /** 智能客服接待中。 */
    public static final String SERVICE_AI = "AI";

    /** 买家已请求转人工，等待客服接入。 */
    public static final String SERVICE_WAITING = "WAITING";

    /** 人工客服接待中。 */
    public static final String SERVICE_HUMAN = "HUMAN";

    /** 会话标题长度上限。 */
    private static final int TITLE_LIMIT = 30;

    /** 统计取样的会话条数上限，防止把全部历史读进内存。 */
    private static final int STATS_SAMPLE_LIMIT = 2000;

    private final AiConversationMapper conversationMapper;

    private final AiMessageMapper messageMapper;

    /**
     * 创建会话管理服务。
     *
     * @param conversationMapper 会话 Mapper
     * @param messageMapper 消息 Mapper
     * @author Henfon
     * @date 2026-09-21
     */
    public AiConversationService(AiConversationMapper conversationMapper, AiMessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    /**
     * 取当前会话，不存在则新建。
     *
     * 归属校验放在这里：会话已绑定会员时，只允许本人继续；会话未绑定会员（匿名发起）时
     * 允许本次登录的会员接管并绑定。前者防止拿到别人的会话标识读走聊天记录，后者不用
     * 为了"登录后还能接着刚才那句话问"而额外开一条迁移逻辑。
     *
     * @param conversationId 会话标识，首轮为空
     * @param channel 入口渠道
     * @param memberId 当前会员 ID，未登录为空
     * @param question 本轮问题，用于生成会话标题
     * @param subjectType 绑定的业务对象类型，可为空
     * @param subjectId 绑定的业务对象标识，可为空
     * @return 会话
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public AiConversation getOrCreate(String conversationId,
                                      String channel,
                                      Long memberId,
                                      String question,
                                      String subjectType,
                                      String subjectId) {
        if (StringUtils.hasText(conversationId)) {
            AiConversation existing = findAccessible(conversationId, channel, memberId);
            if (existing != null) {
                if (existing.getMemberId() == null && memberId != null) {
                    existing.setMemberId(memberId);
                    conversationMapper.updateById(existing);
                }
                return existing;
            }
        }
        AiConversation conversation = new AiConversation();
        conversation.setConversationId(UUID.randomUUID().toString().replace("-", ""));
        conversation.setChannel(channel);
        conversation.setMemberId(memberId);
        conversation.setSubjectType(subjectType);
        conversation.setSubjectId(subjectId);
        conversation.setTitle(buildTitle(question));
        conversation.setMessageCount(0);
        conversation.setStatus(STATUS_ACTIVE);
        // 显式给出接待模式，不依赖列默认值：新建会话一律由智能客服先接待。
        conversation.setServiceMode(SERVICE_AI);
        conversationMapper.insert(conversation);
        return conversation;
    }

    /**
     * 读取会话并校验访问权限。
     *
     * 归属规则：会话已绑定会员时只允许本人访问；会话未绑定会员（匿名发起）时任何访客都能
     * 拿着标识继续，这不算越权——匿名对话本来就没有归属。沿用登录态接管同一条会话的做法，
     * 是为了不让买家登录后丢掉刚才那段对话。
     *
     * @param conversationId 会话标识
     * @param channel 入口渠道
     * @param memberId 当前会员 ID，未登录为空
     * @return 会话，不存在返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    public AiConversation findAccessible(String conversationId, String channel, Long memberId) {
        AiConversation conversation = findByConversationId(conversationId);
        if (conversation == null) {
            return null;
        }
        requireSameChannel(conversation, channel);
        if (conversation.getMemberId() != null && !conversation.getMemberId().equals(memberId)) {
            throw new BusinessException("AI_CONVERSATION_FORBIDDEN", "该会话不属于当前账号");
        }
        return conversation;
    }

    /**
     * 读取会话，不存在或无权访问时抛出业务异常。
     *
     * @param conversationId 会话标识
     * @param channel 入口渠道
     * @param memberId 当前会员 ID，未登录为空
     * @return 会话
     * @author Henfon
     * @date 2026-09-21
     */
    public AiConversation requireAccessible(String conversationId, String channel, Long memberId) {
        AiConversation conversation = findAccessible(conversationId, channel, memberId);
        if (conversation == null) {
            throw new BusinessException("AI_CONVERSATION_NOT_FOUND", "会话不存在或已过期");
        }
        return conversation;
    }

    /**
     * 按会话标识查询。
     *
     * @param conversationId 会话标识
     * @return 会话，不存在返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    public AiConversation findByConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return null;
        }
        return conversationMapper.selectOne(Wrappers.lambdaQuery(AiConversation.class)
                .eq(AiConversation::getConversationId, conversationId)
                .last("LIMIT 1"));
    }

    /**
     * 刷新会话的消息计数与最近消息时间。
     *
     * @param conversationId 会话标识
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public void touch(String conversationId) {
        AiConversation conversation = findByConversationId(conversationId);
        if (conversation == null) {
            return;
        }
        long count = messageMapper.selectCount(Wrappers.lambdaQuery(AiMessage.class)
                .eq(AiMessage::getConversationId, conversationId));
        conversation.setMessageCount((int) count);
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }

    /**
     * 买家主动结束会话：把人工接待或排队交回智能客服。
     *
     * 与客服侧的 {@link #endAgentService} 区别在两点：不校验客服身份（买家是另一方），以及
     * 允许结束排队中的会话——等不到人想撤回去继续问智能客服是合理需求。条件更新兜住并发：
     * 客服恰好在同一刻接入时，serviceMode 已经不是 WAITING 了，这次更新落空并返回 null，
     * 由调用方重新读一次状态，避免把刚接上的会话又关掉。
     *
     * 判据只看 serviceMode，不看 agentEndedAt：一条会话可以被人工接待多轮（结束/被回收之后
     * 买家又转了一次人工），agentEndedAt 记的是上一轮的收尾时间，拿它当"这一轮还没结束"的
     * 前置条件会让第二轮的买家怎么点都退不回去。
     *
     * @param conversationId 会话标识
     * @return 更新后的会话，状态已不是待结束时返回 null
     * @author Henfon
     * @date 2026-09-22
     */
    @Transactional
    public AiConversation endByMember(String conversationId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = conversationMapper.update(null, Wrappers.lambdaUpdate(AiConversation.class)
                .eq(AiConversation::getConversationId, conversationId)
                .in(AiConversation::getServiceMode, SERVICE_WAITING, SERVICE_HUMAN)
                .set(AiConversation::getServiceMode, SERVICE_AI)
                .set(AiConversation::getAgentEndedAt, now)
                .set(AiConversation::getUpdatedAt, now));
        if (updated == 0) {
            return null;
        }
        log.info("会话 {} 由买家结束，已交回智能客服", conversationId);
        return findByConversationId(conversationId);
    }

    /**
     * 人工接待中的会话，按最后活动时间排序，供空闲催办与回收扫描。
     *
     * 只取还在 HUMAN、还没结束的：已经结束的会话不该被再次处理；等待接入的（WAITING）也不是
     * 这里的事——那边买家在等客服，超时该做的是取消排队而不是"结束一次没发生过的服务"。
     *
     * @param limit 返回条数上限
     * @return 会话列表，最后活动时间正序（最久没动静的排前面）
     * @author Henfon
     * @date 2026-09-22
     */
    public List<AiConversation> servingSessions(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        List<AiConversation> rows = conversationMapper.selectList(Wrappers.lambdaQuery(AiConversation.class)
                .eq(AiConversation::getChannel, CHANNEL_PORTAL)
                .eq(AiConversation::getServiceMode, SERVICE_HUMAN)
                .isNull(AiConversation::getAgentEndedAt)
                .orderByAsc(AiConversation::getAgentJoinedAt)
                .last("LIMIT " + safeLimit));
        return rows == null ? List.of() : rows;
    }

    /**
     * 由系统结束人工接待：客服长时间没有动静，会话被自动收回。
     *
     * 与客服手动结束分开一个方法，而不是给 {@link #endAgentService} 传个 null 客服 ID：
     * 手动结束必须校验"是不是本人接管的"，系统结束没有这个前置条件，混在一个方法里会让
     * 那条"只允许接管的客服结束"的约束变得可以绕过。
     *
     * 记 agentEndedAt 但保留 agentId：评价服务要求两者同时非空才允许打分，保留 agentId 意味着
     * 这次服务仍可被评价——买家确实被这位客服接待过，只是客服忘了收尾，评价口径不该因此改变。
     * 保留后本次会话会自动进入买家的「待评价」列表，由门户那条 returnToAi + 弹评价的现成链路
     * 收口。
     *
     * @param conversationId 会话标识
     * @param idleMinutes 这次回收判定的空闲分钟数，只用于日志
     * @return 更新后的会话
     * @author Henfon
     * @date 2026-09-22
     */
    @Transactional
    public AiConversation closeForIdle(String conversationId, long idleMinutes) {
        AiConversation conversation = findByConversationId(conversationId);
        if (conversation == null || !SERVICE_HUMAN.equals(conversation.getServiceMode())
                || conversation.getAgentEndedAt() != null) {
            return null;
        }
        conversation.setServiceMode(SERVICE_AI);
        conversation.setAgentEndedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        log.info("会话 {} 空闲 {} 分钟无消息，已自动结束人工接待", conversationId, idleMinutes);
        return conversation;
    }

    /**
     * 把会话标记为已转人工。
     *
     * @param conversationId 会话标识
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public void markTicketed(String conversationId) {
        AiConversation conversation = findByConversationId(conversationId);
        if (conversation == null) {
            return;
        }
        conversation.setStatus(STATUS_TICKETED);
        conversationMapper.updateById(conversation);
    }

    /**
     * 买家请求转人工，把会话放进等待队列。
     *
     * 重复请求不刷新排队时间：买家连点几次「转人工」不该让等待时长重新计时，否则先来的人
     * 反而排到后面。已经在人工接待中的会话也直接返回，不重新排队。
     *
     * @param conversationId 会话标识
     * @param memberId 当前会员 ID
     * @return 更新后的会话
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public AiConversation requestAgent(String conversationId, Long memberId) {
        AiConversation conversation = requireAccessible(conversationId, CHANNEL_PORTAL, memberId);
        String mode = conversation.getServiceMode();
        if (SERVICE_HUMAN.equals(mode) || SERVICE_WAITING.equals(mode)) {
            return conversation;
        }
        // 匿名聊到一半才登录再转人工是常见路径，这里把归属补上，否则等待队列里只看到一个
        // 无主的会话，客服接了也不知道对方是谁。
        if (conversation.getMemberId() == null) {
            conversation.setMemberId(memberId);
        }
        conversation.setServiceMode(SERVICE_WAITING);
        conversation.setAgentRequestedAt(LocalDateTime.now());
        conversation.setAgentEndedAt(null);
        conversationMapper.updateById(conversation);
        return conversation;
    }

    /**
     * 买家一进来就要求人工：新开一条会话并直接放进等待队列。
     *
     * 标题固定为「请求人工客服」而不是取提问：这条会话在转人工之前没有任何提问，客服在队列里
     * 看到的就是这个标题，写明来意比留一个"新会话"更有用。
     *
     * @param memberId 当前会员 ID
     * @return 更新后的会话
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public AiConversation requestAgentInNewConversation(Long memberId) {
        AiConversation conversation = getOrCreate(null, CHANNEL_PORTAL, memberId, "请求人工客服", null, null);
        return requestAgent(conversation.getConversationId(), memberId);
    }

    /**
     * 客服接管会话，抢单式。
     *
     * 用条件更新而不是先查后改：两个客服同时点「接入」时只有一个能命中 serviceMode='WAITING'
     * 这个条件，另一个拿到 0 行受影响并收到明确提示。同一位客服重复点击是幂等的，不算冲突。
     *
     * 接入时必须把 agentEndedAt 清空：这条会话可能刚被上一轮人工接待用过（客服结束后买家又转
     * 了一次人工，或上一轮被空闲回收自动收尾）。留着上一轮的结束时间，会让这一轮看起来"已经结束
     * 过" —— 买家想主动结束时的条件更新（按 agentEndedAt IS NULL 判断）会一条都匹配不上，
     * 于是怎么点都退不回智能客服。
     *
     * @param conversationId 会话标识
     * @param agentId 客服管理员 ID
     * @return 更新后的会话
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public AiConversation joinAsAgent(String conversationId, Long agentId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = conversationMapper.update(null, Wrappers.lambdaUpdate(AiConversation.class)
                .eq(AiConversation::getConversationId, conversationId)
                .eq(AiConversation::getServiceMode, SERVICE_WAITING)
                .set(AiConversation::getServiceMode, SERVICE_HUMAN)
                .set(AiConversation::getAgentId, agentId)
                .set(AiConversation::getAgentJoinedAt, now)
                .set(AiConversation::getAgentEndedAt, null)
                .set(AiConversation::getUpdatedAt, now));
        if (updated == 0) {
            AiConversation existing = findByConversationId(conversationId);
            if (existing == null) {
                throw new BusinessException("AI_CONVERSATION_NOT_FOUND", "会话不存在或已过期");
            }
            if (SERVICE_HUMAN.equals(existing.getServiceMode()) && agentId.equals(existing.getAgentId())) {
                return existing;
            }
            throw new BusinessException("AI_AGENT_TAKEN", "该会话已被其他客服接入");
        }
        return findByConversationId(conversationId);
    }

    /**
     * 结束人工接待，会话退回智能客服。
     *
     * 只允许接管的这位客服结束：另一位客服点错一行就把别人的会话关掉，是很容易发生的事。
     *
     * @param conversationId 会话标识
     * @param agentId 客服管理员 ID
     * @return 更新后的会话
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public AiConversation endAgentService(String conversationId, Long agentId) {
        AiConversation conversation = findByConversationId(conversationId);
        if (conversation == null) {
            throw new BusinessException("AI_CONVERSATION_NOT_FOUND", "会话不存在或已过期");
        }
        if (!SERVICE_HUMAN.equals(conversation.getServiceMode())) {
            throw new BusinessException("AI_AGENT_NOT_SERVING", "该会话当前不是人工接待中");
        }
        if (agentId != null && !agentId.equals(conversation.getAgentId())) {
            throw new BusinessException("AI_AGENT_FORBIDDEN", "该会话由其他客服接待");
        }
        conversation.setServiceMode(SERVICE_AI);
        conversation.setAgentEndedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        return conversation;
    }

    /**
     * 等待人工接入的会话，先来先服务。
     *
     * @param limit 返回条数上限
     * @return 会话列表
     * @author Henfon
     * @date 2026-09-21
     */
    public List<AiConversation> waitingQueue(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return conversationMapper.selectList(Wrappers.lambdaQuery(AiConversation.class)
                .eq(AiConversation::getChannel, CHANNEL_PORTAL)
                .eq(AiConversation::getServiceMode, SERVICE_WAITING)
                .orderByAsc(AiConversation::getAgentRequestedAt)
                .last("LIMIT " + safeLimit));
    }

    /**
     * 排队等待接入的会话总数。
     *
     * 与 waitingQueue 分开：队列要带上限（客服界面一次展示不了几十条），而"前面还有几个人"
     * 是买家决策要用的数字，被上限截断就会显示出偏小的排队人数。
     *
     * @return 排队会话数
     * @author Henfon
     * @date 2026-09-21
     */
    public long countWaiting() {
        Long count = conversationMapper.selectCount(Wrappers.lambdaQuery(AiConversation.class)
                .eq(AiConversation::getChannel, CHANNEL_PORTAL)
                .eq(AiConversation::getServiceMode, SERVICE_WAITING));
        return count == null ? 0L : count;
    }

    /**
     * 最近若干次人工会话的平均接待时长。
     *
     * 用最近 20 次而不是全部历史：接待时长会随业务与季节漂移，半年前的数据对"还要等多久"
     * 没有参考价值。样本不足时返回 0，由调用方决定兜底值。
     *
     * @return 平均秒数，样本不足时返回 0
     * @author Henfon
     * @date 2026-09-21
     */
    public long averageServeSeconds() {
        List<AiConversation> recent = conversationMapper.selectList(Wrappers.lambdaQuery(AiConversation.class)
                .eq(AiConversation::getChannel, CHANNEL_PORTAL)
                .isNotNull(AiConversation::getAgentJoinedAt)
                .isNotNull(AiConversation::getAgentEndedAt)
                .orderByDesc(AiConversation::getAgentEndedAt)
                .last("LIMIT 20"));
        if (recent == null || recent.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        int counted = 0;
        for (AiConversation conversation : recent) {
            long seconds = Duration.between(conversation.getAgentJoinedAt(),
                    conversation.getAgentEndedAt()).getSeconds();
            if (seconds > 0) {
                total += seconds;
                counted++;
            }
        }
        return counted == 0 ? 0L : total / counted;
    }

    /**
     * 某位会员最近被人工接待过的会话。
     *
     * 供"补评价"使用：买家关掉页面后客服才结束会话的情况很常见，需要回头找出最近这次
     * 还没有评价的服务。
     *
     * @param memberId 会员 ID
     * @param limit 返回条数上限
     * @return 会话列表，结束时间倒序
     * @author Henfon
     * @date 2026-09-21
     */
    public List<AiConversation> recentAgentSessions(Long memberId, int limit) {
        if (memberId == null) {
            return List.of();
        }
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        List<AiConversation> rows = conversationMapper.selectList(Wrappers.lambdaQuery(AiConversation.class)
                .eq(AiConversation::getChannel, CHANNEL_PORTAL)
                .eq(AiConversation::getMemberId, memberId)
                .isNotNull(AiConversation::getAgentId)
                .isNotNull(AiConversation::getAgentEndedAt)
                .orderByDesc(AiConversation::getAgentEndedAt)
                .last("LIMIT " + safeLimit));
        return rows == null ? List.of() : rows;
    }

    /**
     * 指定客服在时间窗口内的人工会话，供统计按人聚合。
     *
     * 只要客服接手过就取（agentJoinedAt 非空），不要求已经结束：结束的会话用来算时长，
     * 没结束的会话由调用方单独识别成"在接"。一次查询取回全部客服的数据再在内存里分组，
     * 而不是每位客服发一次查询——客服人数是个位数，一次查完省掉 N 次往返。
     *
     * 条数上限是兜底：窗口拉得很宽时，取最近 STATS_SAMPLE_LIMIT 条即可，统计本来就是看
     * 趋势，把几万条历史读进内存换来的精度没有意义。
     *
     * @param agentIds 客服账号 ID 列表
     * @param since 窗口起点，为 null 表示不限时间
     * @return 会话列表，接手时间倒序
     * @author Henfon
     * @date 2026-09-21
     */
    public List<AiConversation> humanSessions(List<Long> agentIds, LocalDateTime since) {
        if (agentIds == null || agentIds.isEmpty()) {
            return List.of();
        }
        List<AiConversation> rows = conversationMapper.selectList(Wrappers.lambdaQuery(AiConversation.class)
                .eq(AiConversation::getChannel, CHANNEL_PORTAL)
                .in(AiConversation::getAgentId, agentIds)
                .isNotNull(AiConversation::getAgentJoinedAt)
                .ge(since != null, AiConversation::getAgentJoinedAt, since)
                .orderByDesc(AiConversation::getAgentJoinedAt)
                .last("LIMIT " + STATS_SAMPLE_LIMIT));
        return rows == null ? List.of() : rows;
    }

    /**
     * 按客服统计当前在接会话数。
     *
     * 与 humanSessions 的时间窗口无关：在接是一个"此刻"的状态，切到"近 7 天"不该让手上
     * 还挂着的会话变成 0。只选 agentId 一列，这条查询不需要会话内容。
     *
     * @param agentIds 客服账号 ID 列表
     * @return 账号 ID 到在接会话数的映射，没有在接会话的客服不出现在结果里
     * @author Henfon
     * @date 2026-09-21
     */
    public Map<Long, Long> servingCounts(List<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return Map.of();
        }
        List<AiConversation> rows = conversationMapper.selectList(Wrappers.lambdaQuery(AiConversation.class)
                .select(AiConversation::getAgentId)
                .in(AiConversation::getAgentId, agentIds)
                .eq(AiConversation::getServiceMode, SERVICE_HUMAN));
        Map<Long, Long> counts = new HashMap<>();
        if (rows != null) {
            for (AiConversation row : rows) {
                counts.merge(row.getAgentId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    /**
     * 某位客服正在接待的会话。
     *
     * @param agentId 客服管理员 ID
     * @return 会话列表，最近有消息的在前
     * @author Henfon
     * @date 2026-09-21
     */
    public List<AiConversation> servingBy(Long agentId) {
        if (agentId == null) {
            return List.of();
        }
        return conversationMapper.selectList(Wrappers.lambdaQuery(AiConversation.class)
                .eq(AiConversation::getAgentId, agentId)
                .eq(AiConversation::getServiceMode, SERVICE_HUMAN)
                .orderByDesc(AiConversation::getLastMessageAt));
    }

    /**
     * 读取会话的完整聊天记录。
     *
     * @param conversationId 会话标识
     * @return 消息列表，按会话内序号升序
     * @author Henfon
     * @date 2026-09-21
     */
    public List<AiMessage> messages(String conversationId) {
        return messageMapper.selectList(Wrappers.lambdaQuery(AiMessage.class)
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getSequence));
    }

    /**
     * 回填最后一条助手消息的模型、用量与耗时。
     *
     * 这些字段由框架在流式响应里给出，写入时机在消息已经落库之后，所以只能回填。检索引用
     * 也写在同一列的 JSON 里，但**不覆盖已存在的工具调用快照**：工具调用记录一旦丢了，
     * 多轮对话的工具配对会断掉，比少一份引用严重得多。
     *
     * @param conversationId 会话标识
     * @param model 模型名
     * @param tokensIn 输入 token
     * @param tokensOut 输出 token
     * @param latencyMs 本轮耗时
     * @param refsPayload 检索引用的 JSON 快照，可为空
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public void fillLastAssistantMetrics(String conversationId,
                                         String model,
                                         Integer tokensIn,
                                         Integer tokensOut,
                                         Integer latencyMs,
                                         String refsPayload) {
        AiMessage last = messageMapper.selectOne(Wrappers.lambdaQuery(AiMessage.class)
                .eq(AiMessage::getConversationId, conversationId)
                .eq(AiMessage::getRole, AiMessage.ROLE_ASSISTANT)
                .orderByDesc(AiMessage::getSequence)
                .last("LIMIT 1"));
        if (last == null) {
            return;
        }
        last.setModel(model);
        last.setTokensIn(tokensIn);
        last.setTokensOut(tokensOut);
        last.setLatencyMs(latencyMs);
        if (!StringUtils.hasText(last.getToolPayload()) && StringUtils.hasText(refsPayload)) {
            last.setToolPayload(refsPayload);
        }
        messageMapper.updateById(last);
    }

    /**
     * 校验会话渠道一致。
     *
     * 两个入口共用一套表结构，靠 channel 分区。渠道不一致说明前端把管理端的会话标识传到了
     * 门户接口，属于越权尝试，直接拒绝而不是按门户处理。
     *
     * @param conversation 已有会话
     * @param channel 本次请求的渠道
     * @author Henfon
     * @date 2026-09-21
     */
    private void requireSameChannel(AiConversation conversation, String channel) {
        if (!channel.equals(conversation.getChannel())) {
            throw new BusinessException("AI_CONVERSATION_FORBIDDEN", "会话入口与当前接口不匹配");
        }
    }

    /**
     * 由首条提问生成会话标题。
     *
     * @param question 提问内容
     * @return 会话标题
     * @author Henfon
     * @date 2026-09-21
     */
    private String buildTitle(String question) {
        if (!StringUtils.hasText(question)) {
            return "新会话";
        }
        String text = question.trim().replaceAll("\\s+", " ");
        return text.length() <= TITLE_LIMIT ? text : text.substring(0, TITLE_LIMIT);
    }
}
