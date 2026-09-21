package com.henfon.shop.ai.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.henfon.shop.ai.chat.AiChatMemoryRepository;
import com.henfon.shop.ai.chat.AiPrompts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.template.NoOpTemplateRenderer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话链路的装配。
 *
 * 三件事：把自实现的记忆仓储包成 ChatMemory、按窗口裁剪历史、把 ChatClient 装好。
 *
 * 模板渲染在这里被显式关掉。ChatClient 默认用 ST 模板渲染系统提示与用户输入，花括号会被
 * 当成占位符——检索到的商品文案里带一个花括号，整轮对话就抛异常；买家随手打一个左花括号
 * 同样会失败。提示词与用户输入都不需要变量替换，关掉比逐个转义可靠。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Configuration
@ConditionalOnProperty(prefix = "shop.ai", name = "enabled", havingValue = "true")
public class AiChatConfiguration {

    /**
     * 会话记忆，窗口大小取配置的历史条数上限。
     *
     * 窗口裁掉的是送进模型的上下文，不是聊天记录：ai_message 里始终保留完整历史，
     * 管理端要看的也是完整的那份。
     *
     * @param repository 自实现的记忆仓储
     * @param properties AI 配置
     * @return 会话记忆
     * @author Henfon
     * @date 2026-09-21
     */
    @Bean
    public ChatMemory chatMemory(AiChatMemoryRepository repository, AiProperties properties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(Math.max(2, properties.getChat().getMaxHistoryMessages()))
                .build();
    }

    /**
     * 客服对话客户端。
     *
     * 记忆顾问统一挂在默认顾问链上，会话标识每次请求通过顾问参数传入。系统提示不在这里
     * 写死：每轮检索到的资料不同，由业务层按请求拼装，会话记忆不会保存系统消息，因此不会
     * 把上一轮的资料带进下一轮的上下文。
     *
     * @param chatModel 百炼对话模型
     * @param chatMemory 会话记忆
     * @param properties AI 配置
     * @return 对话客户端
     * @author Henfon
     * @date 2026-09-21
     */
    @Bean
    public ChatClient aiChatClient(ChatModel chatModel, ChatMemory chatMemory, AiProperties properties) {
        AiProperties.Chat chat = properties.getChat();
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(chat.getChatModel())
                .withMaxToken(chat.getMaxTokens())
                .withTemperature(chat.getTemperature())
                .withStream(true)
                .build();
        return ChatClient.builder(chatModel)
                .defaultSystem(AiPrompts.PORTAL_SYSTEM)
                .defaultOptions(options)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTemplateRenderer(new NoOpTemplateRenderer())
                .build();
    }
}
