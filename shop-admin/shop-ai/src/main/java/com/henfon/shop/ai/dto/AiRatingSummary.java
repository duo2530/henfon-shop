package com.henfon.shop.ai.dto;

import java.util.List;

/**
 * 会话评价的汇总统计。
 *
 * 平均分与满意率一起给：只看平均分会被少数极端评价带偏，满意率（4 星及以上占比）更贴近
 * 客服自己感受到的水平。distribution 是 1 到 5 星的条数，供管理端画分布。
 *
 * @param total 评价总条数
 * @param average 平均分，无评价时为空
 * @param satisfied 满意条数，4 星及以上
 * @param unsatisfied 不满意条数，2 星及以下
 * @param distribution 各星级条数，下标 0 对应 1 星
 * @author Henfon
 * @date 2026-09-21
 */
public record AiRatingSummary(long total,
                              Double average,
                              long satisfied,
                              long unsatisfied,
                              List<Long> distribution) {
}
