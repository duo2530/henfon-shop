package com.henfon.shop.ai.dto;

/**
 * 买家请求转人工。
 *
 * 会话标识允许为空：买家可以一进来就直接要人工，不必先问一句智能客服再转——把"必须先跟机器
 * 说一句话"当成前置条件，对已经明确知道要人工的人来说是多余的一步。为空时服务端新建一条
 * 人工会话，并在响应里把会话标识带回去。
 *
 * @param conversationId 会话标识，为空表示直接新开一条人工会话
 * @author Henfon
 * @date 2026-09-21
 */
public record AiAgentTransferRequest(String conversationId) {
}
