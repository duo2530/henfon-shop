package com.henfon.shop.ai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 切换坐席状态的请求。
 *
 * @param status 目标状态：ONLINE 上线，BREAK 小休，OFFLINE 离线
 * @author Henfon
 * @date 2026-09-21
 */
public record AiAgentStatusRequest(@NotBlank(message = "请选择坐席状态") String status) {
}
