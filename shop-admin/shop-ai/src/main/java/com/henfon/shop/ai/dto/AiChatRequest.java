package com.henfon.shop.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 门户提问请求。
 *
 * @param conversationId 会话标识，首轮为空，后续轮次带上服务端返回的值
 * @param question 买家问题
 * @param subjectType 会话绑定的业务对象类型，如 PRODUCT，可为空
 * @param subjectId 会话绑定的业务对象标识，如商品 ID，可为空
 * @author Henfon
 * @date 2026-09-21
 */
public record AiChatRequest(String conversationId,
                            @NotBlank(message = "请输入要咨询的问题")
                            @Size(max = 500, message = "问题长度不能超过 500 个字符")
                            String question,
                            String subjectType,
                            String subjectId) {
}
