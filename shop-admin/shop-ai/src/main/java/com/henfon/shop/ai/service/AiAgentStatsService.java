package com.henfon.shop.ai.service;

import com.henfon.shop.ai.dto.AiAgentStatsBoard;
import com.henfon.shop.ai.dto.AiAgentStatsRow;
import com.henfon.shop.ai.dto.AiRatingSummary;
import com.henfon.shop.ai.dto.AiScheduleBoard.AiScheduleAgent;
import com.henfon.shop.ai.entity.AiAgentStatus;
import com.henfon.shop.ai.entity.AiConversation;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客服服务记录统计。
 *
 * 按人聚合，数据来自三处：会话表（接待量、服务时长）、评价表（平均分与好评差评）、坐席状态表
 * （当前在不在线）。三处都是按 agentId 直接过滤，没有跨表的复杂关联——客服人数是个位数，
 * 每个维度一次查询比写一条大 SQL 更好读，出问题时也更容易单独核对某一项。
 *
 * 窗口只作用于会话与评价以外的"历史量"：在接会话数按当前状态统计，不受窗口影响，否则把
 * 窗口切到"今天"就会看到"当前在接 0 人"而工作台上明明挂着会话。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Service
public class AiAgentStatsService {

    /** 默认统计窗口：近 30 天。 */
    private static final int DEFAULT_WINDOW_DAYS = 30;

    /** 统计窗口上限，再往上就变成了"看全部历史"，对排班与考核没有参考价值。 */
    private static final int MAX_WINDOW_DAYS = 365;

    private final AiAgentDirectory directory;

    private final AiAgentStatusService statusService;

    private final AiConversationService conversationService;

    private final AiRatingService ratingService;

    /**
     * 创建客服统计服务。
     *
     * @param directory 客服名册
     * @param statusService 坐席状态服务
     * @param conversationService 会话服务
     * @param ratingService 评价服务
     * @author Henfon
     * @date 2026-09-21
     */
    public AiAgentStatsService(AiAgentDirectory directory,
                               AiAgentStatusService statusService,
                               AiConversationService conversationService,
                               AiRatingService ratingService) {
        this.directory = directory;
        this.statusService = statusService;
        this.conversationService = conversationService;
        this.ratingService = ratingService;
    }

    /**
     * 客服统计看板。
     *
     * @param days 统计窗口天数，为空取默认值，0 表示不限时间
     * @return 看板数据
     * @author Henfon
     * @date 2026-09-21
     */
    public AiAgentStatsBoard board(Integer days) {
        int window = normalizeWindow(days);
        List<AiScheduleAgent> agents = directory.agents();
        if (agents.isEmpty()) {
            return new AiAgentStatsBoard(window, List.of());
        }
        List<Long> agentIds = agents.stream().map(AiScheduleAgent::agentId).toList();
        Map<Long, String> statuses = statusService.effectiveStatuses(agentIds);
        Map<Long, Long> serving = conversationService.servingCounts(agentIds);
        Map<Long, List<AiConversation>> sessions = groupByAgent(
                conversationService.humanSessions(agentIds, since(window)));
        List<AiAgentStatsRow> rows = new ArrayList<>(agents.size());
        for (AiScheduleAgent agent : agents) {
            rows.add(build(agent, statuses, serving, sessions.get(agent.agentId())));
        }
        return new AiAgentStatsBoard(window, rows);
    }

    /**
     * 组装一位客服的统计行。
     *
     * @param agent 客服
     * @param statuses 账号 ID 到坐席状态的映射
     * @param serving 账号 ID 到在接会话数的映射
     * @param sessions 该客服的会话列表，可为空
     * @return 统计行
     * @author Henfon
     * @date 2026-09-21
     */
    private AiAgentStatsRow build(AiScheduleAgent agent,
                                  Map<Long, String> statuses,
                                  Map<Long, Long> serving,
                                  List<AiConversation> sessions) {
        long served = 0L;
        long timed = 0L;
        long totalSeconds = 0L;
        LocalDateTime lastServedAt = null;
        if (sessions != null) {
            for (AiConversation conversation : sessions) {
                if (conversation.getAgentJoinedAt() == null || conversation.getAgentEndedAt() == null) {
                    continue;
                }
                served++;
                long seconds = Duration.between(conversation.getAgentJoinedAt(),
                        conversation.getAgentEndedAt()).getSeconds();
                if (seconds > 0) {
                    totalSeconds += seconds;
                    timed++;
                }
                if (lastServedAt == null || conversation.getAgentEndedAt().isAfter(lastServedAt)) {
                    lastServedAt = conversation.getAgentEndedAt();
                }
            }
        }
        Long average = timed == 0 ? null : totalSeconds / timed;
        AiRatingSummary rating = ratingService.summary(agent.agentId());
        long ratingTotal = rating.total();
        Double satisfaction = ratingTotal == 0 ? null : percent(rating.satisfied(), ratingTotal);
        Double dissatisfaction = ratingTotal == 0 ? null : percent(rating.unsatisfied(), ratingTotal);
        return new AiAgentStatsRow(agent.agentId(),
                agent.agentName(),
                statuses.getOrDefault(agent.agentId(), AiAgentStatus.OFFLINE),
                serving.getOrDefault(agent.agentId(), 0L).intValue(),
                served,
                totalSeconds,
                average,
                ratingTotal,
                rating.average(),
                rating.satisfied(),
                rating.unsatisfied(),
                satisfaction,
                dissatisfaction,
                lastServedAt);
    }

    /**
     * 转百分比并保留一位小数。
     *
     * @param part 分子
     * @param total 分母
     * @return 百分比数值
     * @author Henfon
     * @date 2026-09-21
     */
    private double percent(long part, long total) {
        return Math.round(part * 1000.0 / total) / 10.0;
    }

    /**
     * 按客服分组。
     *
     * @param sessions 会话列表
     * @return 账号 ID 到会话列表的映射
     * @author Henfon
     * @date 2026-09-21
     */
    private Map<Long, List<AiConversation>> groupByAgent(List<AiConversation> sessions) {
        Map<Long, List<AiConversation>> grouped = new HashMap<>();
        for (AiConversation conversation : sessions) {
            if (conversation.getAgentId() == null) {
                continue;
            }
            grouped.computeIfAbsent(conversation.getAgentId(), key -> new ArrayList<>()).add(conversation);
        }
        return grouped;
    }

    /**
     * 规整统计窗口。
     *
     * @param days 原始天数
     * @return 生效天数，0 表示不限时间
     * @author Henfon
     * @date 2026-09-21
     */
    private int normalizeWindow(Integer days) {
        if (days == null) {
            return DEFAULT_WINDOW_DAYS;
        }
        if (days <= 0) {
            return 0;
        }
        return Math.min(days, MAX_WINDOW_DAYS);
    }

    /**
     * 计算窗口起点。
     *
     * 按自然日起算而不是"当前时刻往前 N 天"：客服看的是"近 30 天接待了多少"，用零点作为
     * 边界，同一天多次刷新拿到的数字才稳定。
     *
     * @param window 生效天数
     * @return 起点时间，不限时间时返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    private LocalDateTime since(int window) {
        return window == 0 ? null : LocalDate.now().minusDays(window).atStartOfDay();
    }
}
