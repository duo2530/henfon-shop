package com.henfon.shop.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服坐席在线状态实体。
 *
 * 一位坐席一行，随状态变化就地更新而不是追加流水：买家侧只需要知道"现在有没有人"，
 * 历史上下线记录对当前判断没有价值。上线时间单独记，用于算在岗时长。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Data
@TableName("ai_agent_status")
public class AiAgentStatus {

    /** 在线：可以接入会话。 */
    public static final String ONLINE = "ONLINE";

    /** 小休：仍算在岗，但不参与新会话的接待。 */
    public static final String BREAK = "BREAK";

    /** 离线：不在岗。 */
    public static final String OFFLINE = "OFFLINE";

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 坐席管理员 ID，一位坐席只有一行。 */
    private Long agentId;
    /** 坐席名称快照，避免账号改名后历史记录无法辨认。 */
    private String agentName;
    /** 坐席状态：ONLINE 在线，BREAK 小休，OFFLINE 离线。 */
    private String status;
    /**
     * 最近心跳时间。
     *
     * 判定在线必须同时看状态与心跳：客服下班忘了点下线、浏览器被直接关掉、机器休眠，
     * 这些情况都不会调用下线接口，只有心跳过期能兜住。
     */
    private LocalDateTime lastHeartbeatAt;
    /** 本次上线时间。 */
    private LocalDateTime onlineAt;
    /** 本次下线时间。 */
    private LocalDateTime offlineAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
