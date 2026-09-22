package com.henfon.shop.ai.dto;

import java.util.List;

/**
 * 客服统计页的整块数据。
 *
 * 统计窗口随数据一起返回，而不是只在前端记着：页面上的数字都是窗口内算出来的，切窗口
 * 与实际口径必须一致，把窗口交回服务端回显可以避免"数据没刷新但标签已经换了"这种错位。
 *
 * @param days 统计窗口天数，0 表示不限时间（全部历史）
 * @param agents 各客服的统计行，按账号 ID 升序
 * @author Henfon
 * @date 2026-09-21
 */
public record AiAgentStatsBoard(int days, List<AiAgentStatsRow> agents) {
}
