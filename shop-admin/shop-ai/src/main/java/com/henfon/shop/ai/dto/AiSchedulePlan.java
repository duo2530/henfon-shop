package com.henfon.shop.ai.dto;

import java.time.LocalTime;
import java.util.List;

/**
 * 一键排班的生成结果。
 *
 * 只是计划，不是排班：生成与实际写入分成两步，中间隔着主管的一次确认。排班一旦落库就直接
 * 影响买家看到的"现在是不是服务时间"，批量改错比一条一条改错的代价大得多，所以这里先把
 * 将要发生什么摊开给人看。
 *
 * <p>条目只列变动，没变的班次不出现——一屏 14 格全列出来的话，真正被改动的那一两格反而
 * 看不出来了。提醒是不给也照样排得出来的信息（哪天没人、谁排得太长），它回答的是"这样排
 * 有什么问题"，不是"能不能排"。</p>
 *
 * @param items 将要发生的变动
 * @param warnings 体检提醒
 * @param shiftCounts 每人排到的班次数，客服名到班次
 * @author Henfon
 * @date 2026-09-22
 */
public record AiSchedulePlan(List<Item> items, List<String> warnings, List<String> shiftCounts) {

    /**
     * 一条将要发生的变动。
     *
     * @param change 变动类型
     * @param agentId 客服账号 ID
     * @param agentName 客服展示名
     * @param weekday 周几：1 周一 至 7 周日
     * @param startTime 变动后的开始时间，删除时为空
     * @param endTime 变动后的结束时间，删除时为空
     * @param previousStart 原来的开始时间，新增时为空
     * @param previousEnd 原来的结束时间，新增时为空
     * @author Henfon
     * @date 2026-09-22
     */
    public record Item(Change change,
                       Long agentId,
                       String agentName,
                       Integer weekday,
                       LocalTime startTime,
                       LocalTime endTime,
                       LocalTime previousStart,
                       LocalTime previousEnd) {

        /**
         * 变动类型。
         *
         * @author Henfon
         * @date 2026-09-22
         */
        public enum Change {
            /** 原来没有，新增一条。 */
            ADD,
            /** 原来有，改时间或重新启用。 */
            UPDATE,
            /** 原来有，这次不再覆盖到，删掉。 */
            REMOVE
        }
    }
}
