package com.henfon.shop.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.ai.dto.AiProductCard;
import com.henfon.shop.ai.entity.AiMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 从消息快照里读回商品卡片。
 *
 * 卡片在回答结束时随引文一起写进 ai_message.tool_payload，之后有三条读路径：买家刷新页面走
 * 门户历史接口、买家重新订阅人工会话走快照、客服打开工作台看会话详情。三处都要把卡片解出来，
 * 写在一个地方避免某条路径漏掉——漏掉的表现是"刷新之后卡片没了"，很难想到是读路径的问题。
 *
 * 同一列上还有工具调用（{@code {"toolCalls":...}}）与检索引用（{@code {"refs":...}}），
 * 所以只按 {@code cards} 键取，取不到就当没有——不能让解析失败把整段聊天记录带崩。
 *
 * @author Henfon
 * @date 2026-09-22
 */
@Component
public class AiProductCardReader {

    private static final Logger log = LoggerFactory.getLogger(AiProductCardReader.class);

    private final ObjectMapper objectMapper;

    /**
     * 创建卡片读取器。
     *
     * @param objectMapper JSON 解析器
     * @author Henfon
     * @date 2026-09-22
     */
    public AiProductCardReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析一条消息里的商品卡片。
     *
     * @param message 消息记录
     * @return 卡片列表，没有或解析失败时返回空列表
     * @author Henfon
     * @date 2026-09-22
     */
    public List<AiProductCard> read(AiMessage message) {
        return message == null ? List.of() : read(message.getToolPayload());
    }

    /**
     * 解析快照文本里的商品卡片。
     *
     * @param payload 快照文本，可为空
     * @return 卡片列表，没有或解析失败时返回空列表
     * @author Henfon
     * @date 2026-09-22
     */
    public List<AiProductCard> read(String payload) {
        if (!StringUtils.hasText(payload)) {
            return List.of();
        }
        String text = payload.trim();
        if (!text.startsWith("{")) {
            // 本批之前的快照直接存工具调用数组，里面不可能有卡片。
            return List.of();
        }
        try {
            JsonNode cards = objectMapper.readTree(text).get("cards");
            if (cards == null || cards.isNull() || !cards.isArray()) {
                return List.of();
            }
            List<AiProductCard> parsed = objectMapper.convertValue(cards, new TypeReference<List<AiProductCard>>() {
            });
            return parsed == null ? List.of() : parsed;
        } catch (Exception exception) {
            log.warn("解析商品卡片快照失败：{}", exception.getMessage());
            return List.of();
        }
    }
}
