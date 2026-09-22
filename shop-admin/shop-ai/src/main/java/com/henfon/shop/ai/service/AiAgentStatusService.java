package com.henfon.shop.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.henfon.shop.ai.dto.AiAgentAvailability;
import com.henfon.shop.ai.dto.AiAgentDesk;
import com.henfon.shop.ai.dto.AiRatingSummary;
import com.henfon.shop.ai.entity.AiAgentStatus;
import com.henfon.shop.ai.mapper.AiAgentStatusMapper;
import com.henfon.shop.ai.service.AiScheduleService.ServiceWindow;
import com.henfon.shop.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 坐席在线状态与买家侧可用性。
 *
 * "有没有客服在线"以坐席主动上线的状态为准，排班只决定措辞：服务时段内没人上线，说明客服
 * 临时离开了；时段外没人上线，说明本来就不是服务时间。这两句话对买家的行动指引完全不同——
 * 前者值得再等等，后者应该直接去留言。
 *
 * 心跳是这套判断的兜底。客服下班忘了点下线、浏览器被直接关掉、笔记本合盖休眠，这些都不会
 * 调用下线接口，只看状态字段会让买家对着一个永远不会有人接的队列等下去。心跳过期即视为
 * 离线，状态字段保留原值只是为了让客服自己知道上次是怎么下线的。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Service
public class AiAgentStatusService {

    private static final Logger log = LoggerFactory.getLogger(AiAgentStatusService.class);

    /** 心跳有效期，超过即视为掉线。客户端每 30 秒一次心跳，连续三次没到才判定掉线。 */
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(90);

    /** 没有历史接待时长时的兜底估算值，只用于排队等待提示。 */
    private static final long FALLBACK_SERVE_SECONDS = 180L;

    /** 估算等待时间的下限，避免显示"约 0 分钟"这种没有意义的数字。 */
    private static final long MIN_ESTIMATE_SECONDS = 60L;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final AiAgentStatusMapper statusMapper;

    private final AiConversationService conversationService;

    private final AiScheduleService scheduleService;

    private final AiRatingService ratingService;

    private final AiAgentDirectory directory;

    /**
     * 创建坐席状态服务。
     *
     * @param statusMapper 坐席状态数据访问对象
     * @param conversationService 会话服务
     * @param scheduleService 排班服务
     * @param ratingService 评价服务
     * @param directory 客服名册
     * @author Henfon
     * @date 2026-09-21
     */
    public AiAgentStatusService(AiAgentStatusMapper statusMapper,
                                AiConversationService conversationService,
                                AiScheduleService scheduleService,
                                AiRatingService ratingService,
                                AiAgentDirectory directory) {
        this.statusMapper = statusMapper;
        this.conversationService = conversationService;
        this.scheduleService = scheduleService;
        this.ratingService = ratingService;
        this.directory = directory;
    }

    /**
     * 工作台状态卡。
     *
     * @param agentId 坐席账号 ID
     * @return 状态卡
     * @author Henfon
     * @date 2026-09-21
     */
    public AiAgentDesk desk(Long agentId) {
        if (agentId == null) {
            throw new BusinessException("AI_AGENT_LOGIN_REQUIRED", "请先登录");
        }
        AiAgentStatus row = findByAgent(agentId);
        String status = row == null || row.getStatus() == null ? AiAgentStatus.OFFLINE : row.getStatus();
        boolean expired = !AiAgentStatus.OFFLINE.equals(status) && isHeartbeatExpired(row);
        AiRatingSummary summary = ratingService.summary(agentId);
        return new AiAgentDesk(agentId,
                directory.displayName(agentId),
                status,
                row == null ? null : row.getOnlineAt(),
                expired,
                conversationService.servingBy(agentId).size(),
                (int) conversationService.countWaiting(),
                scheduleService.todayShift(agentId),
                summary.average(),
                summary.total());
    }

    /**
     * 切换坐席状态。
     *
     * 上线会刷新心跳与上线时间；小休保留上线时间（还在班，只是不接新会话）；离线记录下线时间。
     * 状态是幂等的，重复点同一个状态不会产生额外副作用——客服刷新页面后重新点一次上线是常态。
     *
     * @param agentId 坐席账号 ID
     * @param status 目标状态
     * @return 更新后的状态卡
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public AiAgentDesk changeStatus(Long agentId, String status) {
        if (agentId == null) {
            throw new BusinessException("AI_AGENT_LOGIN_REQUIRED", "请先登录");
        }
        String target = normalizeStatus(status);
        LocalDateTime now = LocalDateTime.now();
        AiAgentStatus row = findByAgent(agentId);
        String previous = row == null || row.getStatus() == null ? AiAgentStatus.OFFLINE : row.getStatus();
        if (row == null) {
            row = new AiAgentStatus();
            row.setAgentId(agentId);
        }
        row.setAgentName(directory.displayName(agentId));
        row.setStatus(target);
        row.setLastHeartbeatAt(now);
        if (AiAgentStatus.OFFLINE.equals(target)) {
            row.setOfflineAt(now);
        } else {
            // 上线时间只在真正从离线转进来时刷新：小休后再回来不该被算成新的一次上线，
            // 否则在岗时长会被切成碎片。
            if (AiAgentStatus.OFFLINE.equals(previous) || row.getOnlineAt() == null) {
                row.setOnlineAt(now);
            }
            row.setOfflineAt(null);
        }
        if (row.getId() == null) {
            statusMapper.insert(row);
        } else {
            statusMapper.updateById(row);
        }
        log.info("客服 {} 切换状态为 {}", row.getAgentName(), target);
        return desk(agentId);
    }

    /**
     * 刷新心跳。
     *
     * 只对已上线或小休的坐席生效：工作台打开时会调用它，如果离线也照样续期，等于把"页面开着"
     * 当成"在岗"，客服只是打开看了一眼后台就会被买家当成可接入的人。
     *
     * @param agentId 坐席账号 ID
     * @return 更新后的状态卡
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public AiAgentDesk heartbeat(Long agentId) {
        if (agentId == null) {
            throw new BusinessException("AI_AGENT_LOGIN_REQUIRED", "请先登录");
        }
        AiAgentStatus row = findByAgent(agentId);
        if (row != null && !AiAgentStatus.OFFLINE.equals(row.getStatus())) {
            row.setLastHeartbeatAt(LocalDateTime.now());
            statusMapper.updateById(row);
        }
        return desk(agentId);
    }

    /**
     * 买家侧的可用性。
     *
     * @return 可用性
     * @author Henfon
     * @date 2026-09-21
     */
    public AiAgentAvailability availability() {
        int online = onlineAgentCount();
        int waiting = (int) conversationService.countWaiting();
        ServiceWindow window = scheduleService.todayWindow();
        String windowText = window == null ? null
                : window.start().format(TIME_FORMAT) + "-" + window.end().format(TIME_FORMAT);
        if (online > 0) {
            Long estimate = estimateWaitSeconds(waiting, online);
            String message = waiting == 0
                    ? "客服在线，可直接转人工"
                    : "当前有 " + waiting + " 人排队，预计等待约 " + humanize(estimate);
            return new AiAgentAvailability(true, online, waiting, estimate,
                    AiAgentAvailability.REASON_ONLINE, windowText, message);
        }
        if (window != null && window.covers(LocalTime.now())) {
            return new AiAgentAvailability(false, 0, waiting, null,
                    AiAgentAvailability.REASON_NO_AGENT, windowText,
                    "客服暂时不在线，你可以先留言，客服上线后会尽快回复");
        }
        String message = windowText == null
                ? "今天暂无客服值班，可以先留言，我们会尽快回复"
                : "当前不是客服服务时间（" + windowText + "），可以先留言";
        return new AiAgentAvailability(false, 0, waiting, null,
                AiAgentAvailability.REASON_OFF_HOURS, windowText, message);
    }

    /**
     * 校验当前可以转人工，否则抛出带原因的异常。
     *
     * 买家侧在点按钮之前已经看过一次可用性，这里是第二道闸：页面上的提示可能已经过期（客服
     * 刚下线），而排队中的买家会一直等下去，不如在入口处拦住并给出明确原因。
     *
     * @author Henfon
     * @date 2026-09-21
     */
    public void requireAccepting() {
        AiAgentAvailability availability = availability();
        if (availability.accepting()) {
            return;
        }
        String code = AiAgentAvailability.REASON_OFF_HOURS.equals(availability.reason())
                ? "AI_AGENT_OFF_HOURS"
                : "AI_AGENT_OFFLINE";
        throw new BusinessException(code, availability.message());
    }

    /**
     * 在线坐席数。
     *
     * @return 状态为在线且心跳未过期的坐席数
     * @author Henfon
     * @date 2026-09-21
     */
    public int onlineAgentCount() {
        LocalDateTime deadline = LocalDateTime.now().minus(HEARTBEAT_TTL);
        Long count = statusMapper.selectCount(Wrappers.lambdaQuery(AiAgentStatus.class)
                .eq(AiAgentStatus::getStatus, AiAgentStatus.ONLINE)
                .gt(AiAgentStatus::getLastHeartbeatAt, deadline));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 在线坐席列表，供管理端展示与排班参考。
     *
     * @return 坐席状态列表
     * @author Henfon
     * @date 2026-09-21
     */
    public List<AiAgentStatus> onlineAgents() {
        LocalDateTime deadline = LocalDateTime.now().minus(HEARTBEAT_TTL);
        List<AiAgentStatus> rows = statusMapper.selectList(Wrappers.lambdaQuery(AiAgentStatus.class)
                .eq(AiAgentStatus::getStatus, AiAgentStatus.ONLINE)
                .gt(AiAgentStatus::getLastHeartbeatAt, deadline)
                .orderByAsc(AiAgentStatus::getOnlineAt));
        return rows == null ? List.of() : rows;
    }

    /**
     * 一批坐席的有效状态，心跳过期的一律算离线。
     *
     * 与 desk 的判断口径一致：状态字段是客服自己点的，心跳才是"人还在"的证据。客服关掉
     * 页面没点下线，状态字段会一直是 ONLINE，只看字段会把一个不在电脑前的人显示成在线。
     *
     * @param agentIds 坐席账号 ID 列表
     * @return 账号 ID 到状态的映射，没有状态记录的坐席不出现（由调用方取默认值）
     * @author Henfon
     * @date 2026-09-21
     */
    public Map<Long, String> effectiveStatuses(List<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return Map.of();
        }
        List<AiAgentStatus> rows = statusMapper.selectList(Wrappers.lambdaQuery(AiAgentStatus.class)
                .in(AiAgentStatus::getAgentId, agentIds));
        Map<Long, String> statuses = new HashMap<>();
        if (rows == null) {
            return statuses;
        }
        for (AiAgentStatus row : rows) {
            String status = row.getStatus() == null ? AiAgentStatus.OFFLINE : row.getStatus();
            if (!AiAgentStatus.OFFLINE.equals(status) && isHeartbeatExpired(row)) {
                status = AiAgentStatus.OFFLINE;
            }
            statuses.put(row.getAgentId(), status);
        }
        return statuses;
    }

    /**
     * 估算排队等待时间。
     *
     * 用"平均接待时长 × 排在我前面的人数 ÷ 在线坐席数"：三个人排队配三个客服，和三个人排队
     * 配一个客服，等待时长差三倍，只按排队人数乘平均时长会算出离谱的数字。
     *
     * @param waiting 排队人数
     * @param online 在线坐席数
     * @return 预计等待秒数，无人排队时为 0
     * @author Henfon
     * @date 2026-09-21
     */
    private Long estimateWaitSeconds(int waiting, int online) {
        if (waiting <= 0) {
            return 0L;
        }
        long average = conversationService.averageServeSeconds();
        long base = average > 0 ? average : FALLBACK_SERVE_SECONDS;
        long agents = Math.max(1, online);
        long estimate = base * waiting / agents;
        return Math.max(MIN_ESTIMATE_SECONDS, estimate);
    }

    /**
     * 把秒数说成人话。
     *
     * @param seconds 秒数
     * @return 文案
     * @author Henfon
     * @date 2026-09-21
     */
    private String humanize(Long seconds) {
        if (seconds == null || seconds <= 0) {
            return "1 分钟以内";
        }
        long minutes = Math.max(1, Math.round(seconds / 60.0));
        if (minutes >= 60) {
            return (minutes / 60) + " 小时左右";
        }
        return minutes + " 分钟";
    }

    /**
     * 校验并规整坐席状态。
     *
     * @param status 原始状态
     * @return 规整后的状态
     * @author Henfon
     * @date 2026-09-21
     */
    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim().toUpperCase();
        if (!AiAgentStatus.ONLINE.equals(value)
                && !AiAgentStatus.BREAK.equals(value)
                && !AiAgentStatus.OFFLINE.equals(value)) {
            throw new BusinessException("AI_AGENT_STATUS_INVALID", "坐席状态取值不正确");
        }
        return value;
    }

    /**
     * 查询坐席状态行。
     *
     * @param agentId 坐席账号 ID
     * @return 状态行，不存在返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    private AiAgentStatus findByAgent(Long agentId) {
        return statusMapper.selectOne(Wrappers.lambdaQuery(AiAgentStatus.class)
                .eq(AiAgentStatus::getAgentId, agentId)
                .last("LIMIT 1"));
    }

    /**
     * 判断心跳是否已过期。
     *
     * @param row 状态行
     * @return 过期返回 true
     * @author Henfon
     * @date 2026-09-21
     */
    private boolean isHeartbeatExpired(AiAgentStatus row) {
        if (row == null || row.getLastHeartbeatAt() == null) {
            return true;
        }
        return row.getLastHeartbeatAt().isBefore(LocalDateTime.now().minus(HEARTBEAT_TTL));
    }
}
