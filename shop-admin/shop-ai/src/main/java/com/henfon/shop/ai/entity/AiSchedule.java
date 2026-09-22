package com.henfon.shop.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 客服坐席排班实体。
 *
 * 按周固定班：weekday 表示周几（1 周一 至 7 周日），一个坐席一天一段班，由
 * (agent_id, weekday) 唯一索引保证。排班不参与"有没有人在线"的判定——那是上线状态
 * 与心跳的职责——它只用来算服务时段，决定买家看到的是"客服暂时不在"还是"非服务时间"。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Data
@TableName("ai_schedule")
public class AiSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 坐席管理员 ID。 */
    private Long agentId;
    /** 坐席名称快照。 */
    private String agentName;
    /** 周几：1 周一 至 7 周日。 */
    private Integer weekday;
    /** 班次开始时间。 */
    private LocalTime startTime;
    /** 班次结束时间。 */
    private LocalTime endTime;
    /** 是否启用：1 启用，0 停用。停用后不参与服务时段判断。 */
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
