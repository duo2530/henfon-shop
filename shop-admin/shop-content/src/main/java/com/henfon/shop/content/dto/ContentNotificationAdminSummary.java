package com.henfon.shop.content.dto;

import java.util.List;

/**
 * 后台通知中心统计概览。
 *
 * <p>标题行需要「共几条 / 运营未读几条 / 会员未读几条」，事件类型分布用于渲染筛选项，
 * 一次聚合返回，避免前端逐个类型请求。</p>
 *
 * @param total 通知总数
 * @param adminUnread 运营未读条数
 * @param memberUnread 会员未读条数
 * @param eventTypes 按事件类型分组计数
 * @author Henfon
 * @date 2026-09-18
 */
public record ContentNotificationAdminSummary(
        long total,
        long adminUnread,
        long memberUnread,
        List<EventTypeCount> eventTypes) {

    /**
     * 事件类型分组计数。
     *
     * @param eventType 事件类型
     * @param count 该类型通知条数
     * @author Henfon
     * @date 2026-09-18
     */
    public record EventTypeCount(String eventType, long count) {
    }
}
