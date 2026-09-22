package com.henfon.shop.ai.dto;

import java.time.LocalDateTime;

/**
 * 待评价的人工会话。
 *
 * 客服结束会话时买家可能已经关掉页面——这种情况在客服主动收尾时很常见——所以评价不能只在
 * 结束的那一刻弹一次。买家下次打开客服窗口时，用它把还没评的那次服务补上，否则评价率会
 * 低到没有统计意义。
 *
 * @param conversationId 会话标识
 * @param agentId 接待客服账号 ID
 * @param agentName 客服名称
 * @param title 会话标题，帮买家回忆起是哪一次咨询
 * @param endedAt 会话结束时间
 * @author Henfon
 * @date 2026-09-21
 */
public record AiPendingRating(String conversationId,
                              Long agentId,
                              String agentName,
                              String title,
                              LocalDateTime endedAt) {
}
