package com.henfon.shop.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理端处理工单的请求。
 *
 * @param status 目标状态：PROCESSING 处理中，CLOSED 已关闭，PENDING 重新打开
 * @param handleNote 处理备注，仅运营可见
 * @author Henfon
 * @date 2026-09-21
 */
public record AiTicketHandleRequest(@NotBlank(message = "请选择处理结果")
                                   String status,
                                   @Size(max = 500, message = "处理备注不能超过 500 个字符")
                                   String handleNote) {
}
