package com.henfon.shop.ai.service;

import com.henfon.shop.ai.dto.AiAgentStatsBoard;
import com.henfon.shop.ai.dto.AiAgentStatsDetail;
import com.henfon.shop.ai.dto.AiAgentStatsRow;
import com.henfon.shop.ai.dto.AiRatingSummary;
import com.henfon.shop.ai.dto.AiRatingView;
import com.henfon.shop.ai.dto.AiScheduleBoard.AiScheduleAgent;
import com.henfon.shop.ai.dto.AiScheduleView;
import com.henfon.shop.ai.entity.AiAgentStatus;
import com.henfon.shop.ai.entity.AiConversation;
import com.henfon.shop.ai.entity.AiRating;
import com.henfon.shop.common.exception.BusinessException;
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

    /** 详情页按天趋势最多给这些天，再长的窗口也只看最后一段。 */
    private static final int TREND_DAYS = 30;

    /** 详情页列出的会话明细条数。 */
    private static final int SESSION_DIGEST_LIMIT = 20;

    /** 详情页列出的最近评价条数。 */
    private static final int RATING_LIMIT = 10;

    private final AiAgentDirectory directory;

    private final AiAgentStatusService statusService;

    private final AiConversationService conversationService;

    private final AiRatingService ratingService;

    private final AiScheduleService scheduleService;

    private final AiMemberDirectory memberDirectory;

    /**
     * 创建客服统计服务。
     *
     * @param directory 客服名册
     * @param statusService 坐席状态服务
     * @param conversationService 会话服务
     * @param ratingService 评价服务
     * @param scheduleService 排班服务
     * @param memberDirectory 买家名册
     * @author Henfon
     * @date 2026-09-21
     */
    public AiAgentStatsService(AiAgentDirectory directory,
                               AiAgentStatusService statusService,
                               AiConversationService conversationService,
                               AiRatingService ratingService,
                               AiScheduleService scheduleService,
                               AiMemberDirectory memberDirectory) {
        this.directory = directory;
        this.statusService = statusService;
        this.conversationService = conversationService;
        this.ratingService = ratingService;
        this.scheduleService = scheduleService;
        this.memberDirectory = memberDirectory;
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
     * 一位客服的详细服务数据。
     *
     * 与看板的查询分工：看板是一次查完所有客服再分组（人数是个位数，一次查完省 N 次往返），
     * 详情页只看一个人，按单个人重查一次反而简单——而且这里要多查评价明细与排班，数据量
     * 比列表行大，不适合塞进每个列表请求里。
     *
     * @param agentId 客服管理员 ID
     * @param days 统计窗口天数，为空取默认值，0 表示不限时间
     * @return 详情
     * @author Henfon
     * @date 2026-09-22
     */
    public AiAgentStatsDetail detail(Long agentId, Integer days) {
        AiScheduleAgent agent = requireAgent(agentId);
        int window = normalizeWindow(days);
        List<AiConversation> sessions = conversationService.humanSessions(List.of(agentId), since(window));
        Map<Long, String> statuses = statusService.effectiveStatuses(List.of(agentId));
        Map<Long, Long> serving = conversationService.servingCounts(List.of(agentId));
        List<AiRating> ratings = ratingService.byAgent(agentId);
        Map<String, Integer> scoreByConversation = new HashMap<>();
        ratings.forEach(rating -> scoreByConversation.put(rating.getConversationId(), rating.getScore()));
        Map<Long, AiMemberDirectory.MemberBadge> badges = memberDirectory.badges(
                sessions.stream().map(AiConversation::getMemberId).toList());
        double weeklyHours;
        List<AiScheduleView> shifts = scheduleService.board().schedules().stream()
                .filter(row -> agentId.equals(row.agentId()))
                .filter(row -> Boolean.TRUE.equals(row.enabled()))
                .toList();
        weeklyHours = shifts.stream()
                .mapToDouble(row -> Duration.between(row.startTime(), row.endTime()).toMinutes() / 60.0)
                .sum();
        return new AiAgentStatsDetail(window,
                build(agent, statuses, serving, sessions),
                trend(sessions, window),
                digests(sessions, badges, scoreByConversation),
                ratingService.recent(agentId, RATING_LIMIT),
                shifts.size(),
                Math.round(weeklyHours * 10.0) / 10.0);
    }

    /**
     * 取出仍在名册里的客服。
     *
     * 账号停用或权限收回后统计就查不到这个人了，但历史数据还在。详情页点不进去比列表少一行
     * 更容易让人以为是页面坏了，所以明确提示"已无可统计的权限"。
     *
     * @param agentId 客服管理员 ID
     * @return 名册里的客服
     * @author Henfon
     * @date 2026-09-22
     */
    private AiScheduleAgent requireAgent(Long agentId) {
        if (agentId == null) {
            throw new BusinessException("AI_AGENT_STATS_NOT_FOUND", "客服不存在");
        }
        return directory.agents().stream()
                .filter(agent -> agent.agentId().equals(agentId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("AI_AGENT_STATS_NOT_FOUND",
                        "该账号已不在客服名册里，可能已被停用"));
    }

    /**
     * 按天汇总接待量。
     *
     * 空的日子也要产出一条 0：柱状图若只画有数据的日子，连着休息三天会看起来像只休了一天
     * （两根柱子挨在一起）。
     *
     * @param sessions 窗口内的会话
     * @param window 生效窗口天数，0 表示不限
     * @return 按日期正序的点
     * @author Henfon
     * @date 2026-09-22
     */
    private List<AiAgentStatsDetail.DailyPoint> trend(List<AiConversation> sessions, int window) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(TREND_DAYS - 1);
        if (window > 0) {
            LocalDate windowStart = LocalDate.now().minusDays(window - 1);
            if (windowStart.isAfter(start)) {
                start = windowStart;
            }
        }
        Map<LocalDate, Long> counts = new HashMap<>();
        for (AiConversation conversation : sessions) {
            if (conversation.getAgentJoinedAt() == null) {
                continue;
            }
            LocalDate day = conversation.getAgentJoinedAt().toLocalDate();
            if (!day.isBefore(start) && !day.isAfter(today)) {
                counts.merge(day, 1L, Long::sum);
            }
        }
        List<AiAgentStatsDetail.DailyPoint> points = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            points.add(new AiAgentStatsDetail.DailyPoint(day, counts.getOrDefault(day, 0L)));
        }
        return points;
    }

    /**
     * 组装会话明细，只取最近若干条。
     *
     * @param sessions 窗口内的会话，接手时间倒序
     * @param badges 买家身份
     * @param scoreByConversation 会话标识到评分的映射
     * @return 会话摘要
     * @author Henfon
     * @date 2026-09-22
     */
    private List<AiAgentStatsDetail.SessionDigest> digests(List<AiConversation> sessions,
                                                           Map<Long, AiMemberDirectory.MemberBadge> badges,
                                                           Map<String, Integer> scoreByConversation) {
        return sessions.stream()
                .limit(SESSION_DIGEST_LIMIT)
                .map(conversation -> {
                    Long seconds = null;
                    if (conversation.getAgentJoinedAt() != null && conversation.getAgentEndedAt() != null) {
                        long raw = Duration.between(conversation.getAgentJoinedAt(),
                                conversation.getAgentEndedAt()).getSeconds();
                        if (raw > 0) {
                            seconds = raw;
                        }
                    }
                    AiMemberDirectory.MemberBadge badge = conversation.getMemberId() == null
                            ? null
                            : badges.get(conversation.getMemberId());
                    return new AiAgentStatsDetail.SessionDigest(conversation.getConversationId(),
                            badge == null ? null : badge.name(),
                            conversation.getTitle(),
                            conversation.getAgentJoinedAt(),
                            conversation.getAgentEndedAt(),
                            seconds,
                            scoreByConversation.get(conversation.getConversationId()));
                })
                .toList();
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
