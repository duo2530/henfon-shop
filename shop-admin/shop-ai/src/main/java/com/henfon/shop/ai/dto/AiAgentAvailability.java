package com.henfon.shop.ai.dto;

/**
 * 买家侧的转人工可用性。
 *
 * 免登录可查：买家要知道"现在点了有没有人接"，这个判断不需要身份。把它做成独立接口而不是
 * 塞进会话状态里，是因为买家在开口提问之前就需要看到它——决定是找客服还是直接留言。
 *
 * @param accepting 当前是否可以转人工，false 时前端不应放行
 * @param onlineAgents 在线坐席数（已上线且心跳未过期）
 * @param waitingCount 正在排队等待接入的会话数
 * @param estimatedWaitSeconds 预计等待秒数，无需排队时为 0
 * @param reason 不可接入的原因：ONLINE 可接入，NO_AGENT 无人在线，OFF_HOURS 非服务时间
 * @param serviceWindow 服务时段文案，如 09:00-21:00，当天无排班时为空
 * @param message 直接展示给买家的一句话，文案由服务端统一，避免各处措辞不一致
 * @author Henfon
 * @date 2026-09-21
 */
public record AiAgentAvailability(boolean accepting,
                                  int onlineAgents,
                                  int waitingCount,
                                  Long estimatedWaitSeconds,
                                  String reason,
                                  String serviceWindow,
                                  String message) {

    /** 有坐席在线，可以转人工。 */
    public static final String REASON_ONLINE = "ONLINE";

    /** 服务时段内但没有坐席在线。 */
    public static final String REASON_NO_AGENT = "NO_AGENT";

    /** 当前不在服务时段内，当天没有排班或还没到上班时间。 */
    public static final String REASON_OFF_HOURS = "OFF_HOURS";
}
