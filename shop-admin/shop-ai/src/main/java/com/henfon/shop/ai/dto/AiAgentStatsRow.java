package com.henfon.shop.ai.dto;

import java.time.LocalDateTime;

/**
 * 一位客服的服务记录统计。
 *
 * 接待量、服务时长、评价三块分开给，不合成一个"综合得分"：接待量与时长反映工作量，评价
 * 反映质量，把它们加权成一个数字会让"接得多但不满意"和"接得少但都很满意"看起来一样，
 * 反而看不出该找谁谈、该表扬谁。
 *
 * 服务时长只累计已结束的人工会话。进行中的会话还没结束、时长未定，算进去会让数字随着
 * 客服继续打字而变，也会被"客服忘了点结束"这种情况长期拉高——这类会话单独用
 * servingCount 表达"手上还有几单"。
 *
 * 评价为空时平均分与好评差评率都返回 null，由前端显示成「—」：0 分和"没有评价"是两回事，
 * 混在一起会让人以为这位客服被差评过。
 *
 * @param agentId 客服管理员 ID
 * @param agentName 客服展示名称
 * @param status 当前坐席状态：ONLINE 在线，BREAK 小休，OFFLINE 离线
 * @param servingCount 当前正在接待的会话数
 * @param servedCount 统计窗口内已结束的人工会话数
 * @param totalServeSeconds 统计窗口内累计服务秒数，只含时长大于 0 的会话
 * @param averageServeSeconds 单次服务平均秒数，没有可计时的会话时为空
 * @param ratingCount 收到的评价条数
 * @param ratingAverage 平均分，无评价时为空
 * @param satisfied 好评条数，4 星及以上
 * @param unsatisfied 差评条数，2 星及以下
 * @param satisfactionRate 好评率百分比，无评价时为空
 * @param dissatisfactionRate 差评率百分比，无评价时为空
 * @param lastServedAt 最近一次服务结束时间，窗口内没有已完成会话时为空
 * @author Henfon
 * @date 2026-09-21
 */
public record AiAgentStatsRow(Long agentId,
                              String agentName,
                              String status,
                              int servingCount,
                              long servedCount,
                              long totalServeSeconds,
                              Long averageServeSeconds,
                              long ratingCount,
                              Double ratingAverage,
                              long satisfied,
                              long unsatisfied,
                              Double satisfactionRate,
                              Double dissatisfactionRate,
                              LocalDateTime lastServedAt) {
}
