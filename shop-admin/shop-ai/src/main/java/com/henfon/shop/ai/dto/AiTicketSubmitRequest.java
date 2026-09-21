package com.henfon.shop.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 门户提交转人工工单的请求。
 *
 * 联系方式是必填的：转人工的意义在于有人回访，留不下联系方式就只是一条没人能接的留言，
 * 与其收进来堆在后台，不如在提交时就要求填。
 *
 * @param conversationId 来源会话标识，可为空（买家直接留言时没有会话）
 * @param contact 联系方式，手机号或邮箱
 * @param question 买家的问题原文
 * @author Henfon
 * @date 2026-09-21
 */
public record AiTicketSubmitRequest(String conversationId,
                                    @NotBlank(message = "请填写联系方式")
                                    @Size(min = 5, max = 120, message = "联系方式长度需在 5 到 120 个字符之间")
                                    String contact,
                                    @NotBlank(message = "请填写要咨询的问题")
                                    @Size(max = 1000, message = "问题长度不能超过 1000 个字符")
                                    String question) {
}
