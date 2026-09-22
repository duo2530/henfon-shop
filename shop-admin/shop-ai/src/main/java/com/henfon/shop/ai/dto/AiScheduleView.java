package com.henfon.shop.ai.dto;

import com.henfon.shop.ai.entity.AiSchedule;

import java.time.LocalTime;

/**
 * 一条排班记录。
 *
 * @param id 排班记录 ID，未保存时为空
 * @param agentId 坐席管理员 ID
 * @param agentName 坐席名称
 * @param weekday 周几：1 周一 至 7 周日
 * @param startTime 班次开始时间
 * @param endTime 班次结束时间
 * @param enabled 是否启用
 * @author Henfon
 * @date 2026-09-21
 */
public record AiScheduleView(Long id,
                             Long agentId,
                             String agentName,
                             Integer weekday,
                             LocalTime startTime,
                             LocalTime endTime,
                             Boolean enabled) {

    /**
     * 由实体构建。
     *
     * @param schedule 排班实体
     * @return 视图
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiScheduleView from(AiSchedule schedule) {
        return new AiScheduleView(schedule.getId(), schedule.getAgentId(), schedule.getAgentName(),
                schedule.getWeekday(), schedule.getStartTime(), schedule.getEndTime(),
                schedule.getEnabled() != null && schedule.getEnabled() == 1);
    }
}
