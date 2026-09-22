package com.henfon.shop.ai.dto;

import java.time.LocalDateTime;

/**
 * 客服工作台顶部的坐席状态卡。
 *
 * 一次返回客服在这个页面上需要知道的所有"关于自己"的信息：在不在岗、手里几单、队列里
 * 多少人等着、今天几点上班、最近被评了几分。合成一个接口是为了让工作台首屏只发一个请求，
 * 也让这几个数字来自同一次数据库快照，不会出现"队列 2 人但列表 3 条"这种自相矛盾的画面。
 *
 * @param agentId 坐席管理员 ID
 * @param agentName 坐席名称
 * @param status 坐席状态：ONLINE 在线，BREAK 小休，OFFLINE 离线
 * @param onlineAt 本次上线时间，离线时为空
 * @param heartbeatExpired 状态为在线但心跳已过期，用于提示"你可能掉线了"
 * @param servingCount 正在接待的会话数
 * @param waitingCount 等待接入的会话数
 * @param todayShift 今天的班次，未排班时为空
 * @param ratingAverage 平均评分，无评价时为空
 * @param ratingCount 评价条数
 * @author Henfon
 * @date 2026-09-21
 */
public record AiAgentDesk(Long agentId,
                          String agentName,
                          String status,
                          LocalDateTime onlineAt,
                          boolean heartbeatExpired,
                          int servingCount,
                          int waitingCount,
                          AiScheduleView todayShift,
                          Double ratingAverage,
                          Long ratingCount) {
}
