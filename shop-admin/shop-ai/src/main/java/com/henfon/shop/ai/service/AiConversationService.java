package com.henfon.shop.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.henfon.shop.ai.entity.AiConversation;
import com.henfon.shop.ai.entity.AiMessage;
import com.henfon.shop.ai.mapper.AiConversationMapper;
import com.henfon.shop.ai.mapper.AiMessageMapper;
import com.henfon.shop.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
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

    /** 门户买家入口。 */
    public static final String CHANNEL_PORTAL = "PORTAL";

    /** 管理端运营助手入口。 */
    public static final String CHANNEL_ADMIN = "ADMIN";

    /** 会话进行中。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 会话已转人工。 */
    public static final String STATUS_TICKETED = "TICKETED";

    /** 会话标题长度上限。 */
    private static final int TITLE_LIMIT = 30;

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
