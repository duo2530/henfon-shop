package com.henfon.shop.ai.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

/**
 * 一键排班的生成参数。
 *
 * 参数是可复现的完整输入：同一份参数算两遍必须得到同一份计划，所以服务端不读"上次生成到哪了"
 * 这类隐含状态。预览与应用走的是同一次计算——前端确认时回传参数而不是回传它自己拿到的那批
 * 条目，避免"预览的和落库的不是一回事"，也免得信任客户端拼出来的排班内容。
 *
 * <p>缺省取值：每天人数不给算 1 人；星期不给视为整周；单班上限不给表示不拆段（一个人从头
 * 上到尾）；每人每周班次上限不给表示不限；是否清掉未覆盖的旧班次默认不清，只补不删——直接
 * 覆盖会把主管手工调过的班次悄悄改掉。</p>
 *
 * @param startTime 每天覆盖的开始时间
 * @param endTime 每天覆盖的结束时间
 * @param perDay 同一时刻需要几个人在岗
 * @param weekdays 生成哪几天：1 周一 至 7 周日，为空视为整周
 * @param maxShiftHours 单人单班时长上限（小时），超过就把当天拆成首尾相接的几段；为空不拆
 * @param maxShiftsPerAgent 每人每周最多排几个班，用于留休；为空不限
 * @param clearUncovered 是否删除范围内没被这次计划覆盖到的旧班次
 * @author Henfon
 * @date 2026-09-22
 */
public record AiSchedulePlanRequest(@NotNull(message = "请填写开始时间") LocalTime startTime,
                                    @NotNull(message = "请填写结束时间") LocalTime endTime,
                                    @Min(value = 1, message = "每天至少要 1 人") Integer perDay,
                                    @Size(min = 1, message = "至少选一天") List<Integer> weekdays,
                                    Integer maxShiftHours,
                                    Integer maxShiftsPerAgent,
                                    Boolean clearUncovered) {
}
