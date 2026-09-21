package com.henfon.shop.ai.chat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
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

import java.util.ArrayList;
import java.util.List;

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
        List<AiMessage> records = messageMapper.selectList(Wrappers.lambdaQuery(AiMessage.class)
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getSequence));
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
        List<AiMessage> stored = messageMapper.selectList(Wrappers.lambdaQuery(AiMessage.class)
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getSequence));
        int overlapped = overlapLength(stored, messages);
        if (overlapped >= messages.size()) {
            return;
        }
        int sequence = stored.isEmpty() ? 0 : stored.get(stored.size() - 1).getSequence();
        for (int index = overlapped; index < messages.size(); index++) {
            Message message = messages.get(index);
            sequence++;
            AiMessage record = new AiMessage();
            record.setConversationId(conversationId);
            record.setSequence(sequence);
            record.setRole(message.getMessageType().name());
            record.setContent(message.getText());
            fillToolPayload(record, message);
            messageMapper.insert(record);
        }
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
     * @param recordPayload 存储记录中的工具快照，可为空
     * @return 比对签名，无工具信息返回空串
     * @author Henfon
     * @date 2026-09-21
     */
    private String toolSignature(String recordPayload) {
        return recordPayload == null ? "" : recordPayload;
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
     * @param record 消息记录
     * @param message 框架消息
     * @author Henfon
     * @date 2026-09-21
     */
    private void fillToolPayload(AiMessage record, Message message) {
        try {
            if (message instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
                record.setToolPayload(objectMapper.writeValueAsString(assistant.getToolCalls()));
            } else if (message instanceof ToolResponseMessage toolResponse) {
                record.setToolPayload(objectMapper.writeValueAsString(toolResponse.getResponses()));
            }
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
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<List<AssistantMessage.ToolCall>>() {
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
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<List<ToolResponseMessage.ToolResponse>>() {
            });
        } catch (Exception exception) {
            log.warn("解析工具返回失败：{}", exception.getMessage());
            return List.of();
        }
    }
}
