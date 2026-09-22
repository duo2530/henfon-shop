package com.henfon.shop.ai.dto;

import java.util.List;

/**
 * 排班页面的整块数据。
 *
 * 坐席列表与班次一起返回：候选坐席来自权限反查（谁持有客服工作台权限），服务时段来自
 * 排班表，两者都在服务端拼好后一次下发，前端不需要先查人员再查班次、也不用自己按
 * agentId 做关联。
 *
 * @param agents 可选坐席
 * @param schedules 已有排班
 * @author Henfon
 * @date 2026-09-21
 */
public record AiScheduleBoard(List<AiScheduleAgent> agents, List<AiScheduleView> schedules) {

    /**
     * 可选坐席。
     *
     * @param agentId 坐席管理员 ID
     * @param username 登录账号
     * @param agentName 展示名称
     * @param status 当前在线状态：ONLINE 在线，BREAK 小休，OFFLINE 离线
     * @author Henfon
     * @date 2026-09-21
     */
    public record AiScheduleAgent(Long agentId, String username, String agentName, String status) {
    }
}
