package com.henfon.shop.ai.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一位客服的详细服务数据。
 *
 * 与列表行的分工：列表行是"这一格该写什么数"，详情页是把这些数拆开——同一个人这周排了几小时
 * 班、每天接了多少、每次会话花了多久、买家具体说了什么。只看总分看不出问题出在哪，详情页要
 * 回答的是"为什么是这个数"。
 *
 * <p>窗口口径与列表一致：会话相关的部分（接待量、时长、趋势）按 windowDays 截取，评价是全部
 * 历史。评价之所以不跟着窗口走，是因为列表行的好评率也是全历史的口径，两处必须能对得上，
 * 否则同一个人在列表里显示 92%、点进去显示 88% 就没人敢信任何一个。详情页额外给出
 * ratingCount 与最近评价，够判断这个百分比是建立在几条数据上的。</p>
 *
 * @param windowDays 生效的统计窗口天数，0 表示不限时间
 * @param summary 概览，与列表里那一行同一口径
 * @param trend 按天的接待量，窗口很长时只给最后 30 天
 * @param sessions 最近的会话明细
 * @param ratings 最近收到的评价
 * @param scheduledDays 排班表里启用的天数
 * @param scheduledHours 排班表里启用的周总工时
 * @author Henfon
 * @date 2026-09-22
 */
public record AiAgentStatsDetail(int windowDays,
                                 AiAgentStatsRow summary,
                                 List<DailyPoint> trend,
                                 List<SessionDigest> sessions,
                                 List<AiRatingView> ratings,
                                 int scheduledDays,
                                 double scheduledHours) {

    /**
     * 某一天的接待量。
     *
     * 没有会话的日子也要返回，值为 0：柱状图如果只画有数据的日子，连续休假三天会看起来像
     * 只休了一天（两个柱子挨在一起）。
     *
     * @param date 日期
     * @param served 当天已结束的人工会话数
     * @author Henfon
     * @date 2026-09-22
     */
    public record DailyPoint(LocalDate date, long served) {
    }

    /**
     * 一次会话的摘要。
     *
     * @param conversationId 会话标识
     * @param memberName 买家显示名，匿名或未登录时为空
     * @param title 会话标题
     * @param joinedAt 客服接入时间
     * @param endedAt 服务结束时间，进行中为空
     * @param serveSeconds 本次服务秒数，进行中或时长为 0 时为空
     * @param score 买家给这次服务的评分，未评价为空
     * @author Henfon
     * @date 2026-09-22
     */
    public record SessionDigest(String conversationId,
                                String memberName,
                                String title,
                                LocalDateTime joinedAt,
                                LocalDateTime endedAt,
                                Long serveSeconds,
                                Integer score) {
    }
}
