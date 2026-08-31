package com.henfon.shop.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 业务事件转站内通知请求。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public record NotificationEventRequest(
        @NotNull(message = "会员ID不能为空") Long memberId,
        Long orderId,
        @Size(max = 64, message = "业务标识不能超过64个字符") String businessId,
        @NotBlank(message = "通知事件类型不能为空") @Size(max = 64, message = "通知事件类型不能超过64个字符") String eventType,
        @NotBlank(message = "通知标题不能为空") @Size(max = 200, message = "通知标题不能超过200个字符") String title,
        @NotBlank(message = "通知内容不能为空") @Size(max = 1000, message = "通知内容不能超过1000个字符") String content,
        @NotBlank(message = "通知幂等键不能为空") @Size(max = 200, message = "通知幂等键不能超过200个字符") String dedupeKey) {
}
