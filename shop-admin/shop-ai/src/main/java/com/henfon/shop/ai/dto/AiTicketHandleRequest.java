package com.henfon.shop.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理端处理工单的请求。
 *
 * 备注与回复分成两个字段，不是冗余：备注写的是处理过程（"已电话联系，等客户确认收货地址"），
 * 回复写的是结论（"已为你补发，3 天内送达"）。前者给运营交接用，后者给买家看，混在一个字段里
 * 就只能二选一——要么把过程暴露给买家，要么买家永远看不到答复。
 *
 * @param status 目标状态：PROCESSING 处理中，CLOSED 已关闭，PENDING 重新打开
 * @param handleNote 处理备注，仅运营可见
 * @param reply 给买家的回复内容，门户可见；为空表示本次不更新回复
 * @author Henfon
 * @date 2026-09-21
 */
public record AiTicketHandleRequest(@NotBlank(message = "请选择处理结果")
                                   String status,
                                   @Size(max = 500, message = "处理备注不能超过 500 个字符")
                                   String handleNote,
                                   @Size(max = 1000, message = "回复内容不能超过 1000 个字符")
                                   String reply) {
}
