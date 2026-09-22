package com.henfon.shop.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * 保存一条排班。
 *
 * 按 (坐席, 周几) 覆盖写入：排班表是"未来怎么上班"的设定，重复保存同一天应该是改而不是
 * 追加，否则同一个客服同一天会出现多条互相矛盾的班次，服务时段算出来就没了意义。
 *
 * @param agentId 坐席管理员 ID
 * @param weekday 周几：1 周一 至 7 周日
 * @param startTime 班次开始时间
 * @param endTime 班次结束时间
 * @param enabled 是否启用，缺省视为启用
 * @author Henfon
 * @date 2026-09-21
 */
public record AiScheduleSaveRequest(@NotNull(message = "请选择客服") Long agentId,
                                    @NotNull(message = "请选择星期")
                                    @Min(value = 1, message = "星期取值 1-7")
                                    @Max(value = 7, message = "星期取值 1-7") Integer weekday,
                                    @NotNull(message = "请填写开始时间") LocalTime startTime,
                                    @NotNull(message = "请填写结束时间") LocalTime endTime,
                                    Boolean enabled) {
}
