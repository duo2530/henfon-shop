package com.henfon.shop.ai.chat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.ai.entity.AiMessage;
import com.henfon.shop.ai.mapper.AiMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自实现的会话记忆仓储，落 ai_message 表。
 *
 * 没有用 Spring AI 内置的 JdbcChatMemoryRepository，原因是它**不支持工具调用消息**：
 * 含 tool calls 的 AssistantMessage 与 ToolResponseMessage 在保存时会被静默过滤，
 * 读回来也没有，且不报任何错误。P1 引入 Function Calling 后，表现是"模型记不住刚才
 * 查过的订单号"——排查起来会先怀疑模型，而问题其实在存储层。
 *
 * saveAll 只追加、不删除：ChatMemory 传入的可能是被窗口裁剪过的近期消息，而 ai_message
 * 同时是完整聊天记录，历史不该因为窗口滑动就消失。读回来时由 MessageWindowChatMemory
 * 自行裁剪。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Component
public class AiChatMemoryRepository implements ChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(AiChatMemoryRepository.class);

    private final AiMessageMapper messageMapper;

    private final ObjectMapper objectMapper;

    /**
     * 创建会话记忆仓储。
     *
     * @param messageMapper 消息明细 Mapper
     * @param objectMapper JSON 序列化器
     * @author Henfon
     * @date 2026-09-21
     */
    public AiChatMemoryRepository(AiMessageMapper messageMapper, ObjectMapper objectMapper) {
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询全部会话标识。
     *
     * @return 会话标识列表
     * @author Henfon
     * @date 2026-09-21
     */
    @Override
    public List<String> findConversationIds() {
        return messageMapper.selectList(Wrappers.lambdaQuery(AiMessage.class)
                        .select(AiMessage::getConversationId)
                        .groupBy(AiMessage::getConversationId))
                .stream()
                .map(AiMessage::getConversationId)
                .toList();
    }

    /**
     * 按会话读取消息，按会话内序号升序。
     *
     * @param conversationId 会话标识
     * @return 消息列表
     * @author Henfon
     * @date 2026-09-21
     */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<AiMessage> records = memoryRecords(conversationId);
        List<Message> messages = new ArrayList<>(records.size());
        for (AiMessage record : records) {
            Message message = toMessage(record);
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    /**
     * 读取参与会话记忆的消息，人工客服的回复不在其中。
     *
     * 人工消息以 ASSISTANT 角色落库（买家那边要靠这个角色渲染成客服气泡），若读回上下文，
     * 模型会把客服说过的话当成自己说过的话，下一轮顺着编——买家刚被客服告知"这个订单已退款"，
     * 模型接着就会以客服的口吻继续承诺。买家看到的聊天记录里仍然有这几条，只是不喂给模型。
     *
     * 这层过滤必须显式写在查询里，不能指望下游"遇到不认识的发送方就跳过"：那种跳过依赖
     * 具体实现的行为，换个版本就可能失效，而且被跳过的消息仍然占着记忆窗口的名额。
     *
     * @param conversationId 会话标识
     * @return 消息记录，按会话内序号升序
     * @author Henfon
     * @date 2026-09-21
     */
    private List<AiMessage> memoryRecords(String conversationId) {
        return messageMapper.selectList(Wrappers.lambdaQuery(AiMessage.class)
                .eq(AiMessage::getConversationId, conversationId)
                // sender 为 NULL 的是本批之前的历史数据与工具消息，一并算作模型自己的上下文。
                .and(wrapper -> wrapper.isNull(AiMessage::getSender)
                        .or()
                        .ne(AiMessage::getSender, AiMessage.SENDER_AGENT))
                .orderByAsc(AiMessage::getSequence));
    }

    /**
     * 读取会话当前的最大消息序号。
     *
     * @param conversationId 会话标识
     * @return 最大序号，没有消息时返回 0
     * @author Henfon
     * @date 2026-09-21
     */
    private int maxSequence(String conversationId) {
        AiMessage last = messageMapper.selectOne(Wrappers.lambdaQuery(AiMessage.class)
                .eq(AiMessage::getConversationId, conversationId)
                .orderByDesc(AiMessage::getSequence)
                .last("LIMIT 1"));
        return last == null || last.getSequence() == null ? 0 : last.getSequence();
    }

    /**
     * 追加保存消息。
     *
     * @param conversationId 会话标识
     * @param messages 窗口内的消息列表
     * @author Henfon
     * @date 2026-09-21
     */
    @Override
    @Transactional
    public void saveAll(String conversationId, List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        List<AiMessage> stored = memoryRecords(conversationId);
        int overlapped = overlapLength(stored, messages);
        if (overlapped >= messages.size()) {
            return;
        }
        // 序号取全表最大值，不能用记忆窗口的最后一条递推：人工客服的消息也占序号，按窗口
        // 末尾加一会正好撞上人工消息已经用掉的号，唯一索引当场拒绝插入，表现为"人工接入后
        // AI 再也没回过话"。
        int sequence = maxSequence(conversationId);
        for (int index = overlapped; index < messages.size(); index++) {
            Message message = messages.get(index);
            sequence++;
            AiMessage record = new AiMessage();
            record.setConversationId(conversationId);
            record.setSequence(sequence);
            record.setRole(message.getMessageType().name());
            record.setSender(resolveSender(message));
            record.setContent(message.getText());
            fillToolPayload(record, message);
            messageMapper.insert(record);
        }
    }

    /**
     * 推断这条模型侧消息的发送方。
     *
     * 必须显式写，不能留给读取侧按角色兜底：本轮之前的历史数据是迁移脚本按角色回填的（显式的
     * MEMBER / AI），新写入的行若留空，同一列上就会出现"一部分有值、一部分为 NULL"两种口径，
     * 任何按 `sender = 'AI'` 过滤的查询都会静默漏掉新数据。工具消息与系统消息保持为空——它们
     * 不对应任何一个"发送方"，也不是给买家看的内容。
     *
     * @param message 框架消息
     * @return 发送方，工具与系统消息返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    private String resolveSender(Message message) {
        MessageType type = message.getMessageType();
        if (MessageType.USER.equals(type)) {
            return AiMessage.SENDER_MEMBER;
        }
        if (MessageType.ASSISTANT.equals(type)) {
            return AiMessage.SENDER_AI;
        }
        return null;
    }

    /**
     * 计算库中已有记录与本次传入消息的重叠长度。
     *
     * 不能用"总数对比"判断有没有新消息：MessageWindowChatMemory 传进来的是最近若干条，
     * 头部会被窗口裁掉，窗口填满后长度恒等于上限值，第 N 轮之后的新消息会被误判成
     * "全是旧消息"而永远不落库——表现是对话到第 7 轮左右开始"失忆"，且不报错。
     *
     * 传入的列表一定落在库中记录的尾部（窗口只会从头部丢消息），所以从最长可能的
     * 重叠开始逐段比对，第一段完全一致的即为重叠部分。
     *
     * @param stored 库中已有记录，按序号升序
     * @param incoming 本次传入的消息窗口
     * @return 重叠的消息条数，没有重叠返回 0
     * @author Henfon
     * @date 2026-09-21
     */
    private int overlapLength(List<AiMessage> stored, List<Message> incoming) {
        int maxLength = Math.min(stored.size(), incoming.size());
        for (int length = maxLength; length > 0; length--) {
            int offset = stored.size() - length;
            boolean matched = true;
            for (int index = 0; index < length; index++) {
                if (!isSameMessage(stored.get(offset + index), incoming.get(index))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return length;
            }
        }
        return 0;
    }

    /**
     * 判断存储记录与框架消息是否同一条。
     *
     * 比对角色、正文与工具调用快照三部分。只比正文是不够的：工具返回消息的正文为空，
     * 两条不同的工具返回会互相判等，导致真正的新消息被当成历史丢掉，而工具消息一旦缺位，
     * 后续请求会因"工具调用没有对应返回"被模型服务端拒绝。
     *
     * @param record 存储记录
     * @param message 框架消息
     * @return 同一条返回 true
     * @author Henfon
     * @date 2026-09-21
     */
    private boolean isSameMessage(AiMessage record, Message message) {
        String role = record.getRole() == null ? "" : record.getRole();
        if (!role.equalsIgnoreCase(message.getMessageType().name())) {
            return false;
        }
        String storedContent = record.getContent() == null ? "" : record.getContent();
        String incomingContent = message.getText() == null ? "" : message.getText();
        if (!storedContent.equals(incomingContent)) {
            return false;
        }
        return toolSignature(record.getToolPayload()).equals(toolSignature(message));
    }

    /**
     * 计算工具调用信息的比对签名。
     *
     * 只比工具调用部分，不比同一列里附带的检索引擎与商品卡片快照：那两项是回答完成后回填的，
     * 不对应任何框架消息。把整串 JSON 拿去比对，会让「同一条助手回复」在回填之后判等失败，
     * 于是这一轮的消息被当成新消息重复插入一次，聊天记录里出现两遍同样的回答。
     *
     * @param recordPayload 存储记录中的工具快照，可为空
     * @return 比对签名，无工具信息返回空串
     * @author Henfon
     * @date 2026-09-21
     */
    private String toolSignature(String recordPayload) {
        String toolCalls = toolCallsOnly(recordPayload);
        return toolCalls == null ? "" : toolCalls;
    }

    /**
     * 从混合快照里取出工具调用部分。
     *
     * 同一列上现在有三种内容的可能写法，都要能读：纯工具调用数组（本批之前写入的历史数据）、
     * 带 {@code toolCalls} 键的对象、以及只有 refs 或 cards 的对象。取不到就返回原串，
     * 让比对按原样进行——宁可多比一段，也不能把真正的工具调用丢掉。
     *
     * @param payload 快照文本，可为空
     * @return 工具调用部分的 JSON 文本，无工具调用时返回 null
     * @author Henfon
     * @date 2026-09-22
     */
    private String toolCallsOnly(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        String text = payload.trim();
        if (!text.startsWith("{")) {
            // 历史数据直接存的是工具调用数组，原样返回。
            return text;
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            JsonNode toolCalls = node.get("toolCalls");
            return toolCalls == null || toolCalls.isNull() ? null : toolCalls.toString();
        } catch (Exception exception) {
            log.warn("解析工具调用快照失败，按原串比对：{}", exception.getMessage());
            return text;
        }
    }

    /**
     * 计算框架消息中工具调用信息的比对签名。
     *
     * @param message 框架消息
     * @return 比对签名，无工具信息返回空串
     * @author Henfon
     * @date 2026-09-21
     */
    private String toolSignature(Message message) {
        try {
            if (message instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
                return objectMapper.writeValueAsString(assistant.getToolCalls());
            }
            if (message instanceof ToolResponseMessage toolResponse) {
                return objectMapper.writeValueAsString(toolResponse.getResponses());
            }
        } catch (Exception exception) {
            log.warn("计算工具消息比对签名失败：{}", exception.getMessage());
        }
        return "";
    }

    /**
     * 删除会话的全部消息。
     *
     * @param conversationId 会话标识
     * @author Henfon
     * @date 2026-09-21
     */
    @Override
    @Transactional
    public void deleteByConversationId(String conversationId) {
        messageMapper.delete(Wrappers.lambdaQuery(AiMessage.class)
                .eq(AiMessage::getConversationId, conversationId));
    }

    /**
     * 把工具调用信息序列化进 tool_payload，供读回时还原。
     *
     * 与检索引擎、商品卡片共用同一列，所以统一包成带 {@code toolCalls} 键的对象；读回时
     * {@link #readToolCalls(String)} 两种写法都认，历史数据里的纯数组不受影响。
     *
     * @param record 消息记录
     * @param message 框架消息
     * @author Henfon
     * @date 2026-09-21
     */
    private void fillToolPayload(AiMessage record, Message message) {
        try {
            Object toolCalls = null;
            if (message instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
                toolCalls = assistant.getToolCalls();
            } else if (message instanceof ToolResponseMessage toolResponse) {
                toolCalls = toolResponse.getResponses();
            }
            if (toolCalls == null) {
                return;
            }
            record.setToolPayload(objectMapper.writeValueAsString(Map.of("toolCalls", toolCalls)));
        } catch (Exception exception) {
            // 工具信息只是排查线索，序列化失败不该阻断消息落库。
            log.warn("序列化工具消息失败，会话 {}：{}", record.getConversationId(), exception.getMessage());
        }
    }

    /**
     * 把存储记录还原成框架消息。
     *
     * @param record 消息记录
     * @return 框架消息，无法识别时返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    private Message toMessage(AiMessage record) {
        String role = record.getRole() == null ? "" : record.getRole().toUpperCase();
        String content = record.getContent();
        // 与 MessageType 的 name() 比较，不要用 getValue()：前者是 "USER"，后者是小写
        // "user"，拿大写角色去比小写取值永远不会相等，症状是记忆读回恒为空且只打一行
        // "未知消息角色"的日志，多轮对话静默失效。
        if (MessageType.USER.name().equals(role)) {
            return new UserMessage(content);
        }
        if (MessageType.SYSTEM.name().equals(role)) {
            return new SystemMessage(content);
        }
        if (MessageType.TOOL.name().equals(role)) {
            List<ToolResponseMessage.ToolResponse> responses = readToolResponses(record.getToolPayload());
            return new ToolResponseMessage(responses);
        }
        if (MessageType.ASSISTANT.name().equals(role)) {
            List<AssistantMessage.ToolCall> toolCalls = readToolCalls(record.getToolPayload());
            return toolCalls.isEmpty()
                    ? new AssistantMessage(content)
                    : new AssistantMessage(content, java.util.Map.of(), toolCalls);
        }
        log.warn("未知消息角色 {}，已跳过会话 {} 的第 {} 条消息",
                record.getRole(), record.getConversationId(), record.getSequence());
        return null;
    }

    /**
     * 反序列化工具调用列表。
     *
     * @param payload JSON 文本
     * @return 工具调用列表，解析失败返回空列表
     * @author Henfon
     * @date 2026-09-21
     */
    private List<AssistantMessage.ToolCall> readToolCalls(String payload) {
        String text = toolCallsOnly(payload);
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(text, new TypeReference<List<AssistantMessage.ToolCall>>() {
            });
        } catch (Exception exception) {
            log.warn("解析工具调用失败：{}", exception.getMessage());
            return List.of();
        }
    }

    /**
     * 反序列化工具返回列表。
     *
     * @param payload JSON 文本
     * @return 工具返回列表，解析失败返回空列表
     * @author Henfon
     * @date 2026-09-21
     */
    private List<ToolResponseMessage.ToolResponse> readToolResponses(String payload) {
        String text = toolCallsOnly(payload);
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(text, new TypeReference<List<ToolResponseMessage.ToolResponse>>() {
            });
        } catch (Exception exception) {
            log.warn("解析工具返回失败：{}", exception.getMessage());
            return List.of();
        }
    }
}
