package com.henfon.shop.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.henfon.shop.ai.dto.AiPendingRating;
import com.henfon.shop.ai.dto.AiRatingRequest;
import com.henfon.shop.ai.dto.AiRatingSummary;
import com.henfon.shop.ai.dto.AiRatingView;
import com.henfon.shop.ai.entity.AiConversation;
import com.henfon.shop.ai.entity.AiRating;
import com.henfon.shop.ai.mapper.AiRatingMapper;
import com.henfon.shop.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 人工会话满意度评价。
 *
 * 评价对象是"这一次人工服务"，不是会话本身：会话上可能先问过智能客服、再由人工接手，
 * 让买家给整条会话打分，分数会混进对机器的评价，对客服不公平，也让数据失去意义。所以
 * 只有真正被人工接待过（有坐席、有结束时间）的会话才可评价，且评价里落库的是坐席 ID。
 *
 * 重复提交按幂等处理，直接返回已存在的那条：买家来回点两下不该看到"你已经评过了"这种
 * 报错，也不该覆盖掉第一次的真实感受。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Service
public class AiRatingService {

    private static final Logger log = LoggerFactory.getLogger(AiRatingService.class);

    /** 快捷标签上限，超出部分截断。 */
    private static final int MAX_TAGS = 5;

    /** 可补评价的回溯天数。太久之前的服务买家已经记不清，弹出来只会让人反感。 */
    private static final int PENDING_WINDOW_DAYS = 7;

    /** 查补评价时最多回看几次人工服务。 */
    private static final int PENDING_SCAN_LIMIT = 10;

    private final AiRatingMapper ratingMapper;

    private final AiConversationService conversationService;

    private final AiAgentDirectory directory;

    /**
     * 创建评价服务。
     *
     * @param ratingMapper 评价数据访问对象
     * @param conversationService 会话服务
     * @param directory 客服名册
     * @author Henfon
     * @date 2026-09-21
     */
    public AiRatingService(AiRatingMapper ratingMapper,
                           AiConversationService conversationService,
                           AiAgentDirectory directory) {
        this.ratingMapper = ratingMapper;
        this.conversationService = conversationService;
        this.directory = directory;
    }

    /**
     * 提交评价。
     *
     * @param memberId 当前会员 ID
     * @param request 评价内容
     * @return 评价
     * @author Henfon
     * @date 2026-09-21
     */
    @Transactional
    public AiRatingView submit(Long memberId, AiRatingRequest request) {
        if (memberId == null) {
            throw new BusinessException("AI_RATING_LOGIN_REQUIRED", "请先登录后再评价");
        }
        AiConversation conversation = conversationService.requireAccessible(request.conversationId(),
                AiConversationService.CHANNEL_PORTAL, memberId);
        if (conversation.getAgentId() == null || conversation.getAgentEndedAt() == null) {
            throw new BusinessException("AI_RATING_NOT_APPLICABLE", "这次咨询没有人工客服接待，暂时不能评价");
        }
        AiRating existing = findByConversation(request.conversationId());
        if (existing != null) {
            return AiRatingView.from(existing);
        }
        AiRating rating = new AiRating();
        rating.setConversationId(request.conversationId());
        rating.setMemberId(memberId);
        rating.setAgentId(conversation.getAgentId());
        rating.setAgentName(directory.displayName(conversation.getAgentId()));
        rating.setScore(request.score());
        rating.setTags(joinTags(request.tags()));
        rating.setComment(StringUtils.hasText(request.comment()) ? request.comment().trim() : null);
        rating.setCreatedAt(LocalDateTime.now());
        try {
            ratingMapper.insert(rating);
        } catch (DuplicateKeyException exception) {
            // 同一会话的两次提交撞在唯一索引上，说明另一路已经写进去了，返回那一条即可。
            AiRating concurrent = findByConversation(request.conversationId());
            if (concurrent != null) {
                return AiRatingView.from(concurrent);
            }
            throw exception;
        }
        log.info("会话 {} 收到 {} 星评价，接待客服 {}", request.conversationId(), request.score(),
                rating.getAgentName());
        return AiRatingView.from(rating);
    }

    /**
     * 查出需要买家补评价的最近一次人工服务。
     *
     * @param memberId 当前会员 ID
     * @return 待评价的会话，没有则返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    public AiPendingRating pending(Long memberId) {
        if (memberId == null) {
            return null;
        }
        List<AiConversation> candidates = conversationService.recentAgentSessions(memberId, PENDING_SCAN_LIMIT);
        if (candidates.isEmpty()) {
            return null;
        }
        LocalDateTime earliest = LocalDateTime.now().minusDays(PENDING_WINDOW_DAYS);
        List<AiConversation> inWindow = candidates.stream()
                .filter(item -> item.getAgentEndedAt() != null && item.getAgentEndedAt().isAfter(earliest))
                .toList();
        if (inWindow.isEmpty()) {
            return null;
        }
        Set<String> rated = ratedConversationIds(inWindow.stream().map(AiConversation::getConversationId).toList());
        return inWindow.stream()
                .filter(item -> !rated.contains(item.getConversationId()))
                .max(Comparator.comparing(AiConversation::getAgentEndedAt))
                .map(item -> new AiPendingRating(item.getConversationId(), item.getAgentId(),
                        directory.displayName(item.getAgentId()), item.getTitle(), item.getAgentEndedAt()))
                .orElse(null);
    }

    /**
     * 评价统计。
     *
     * @param agentId 只看某位客服，传 null 表示全部
     * @return 统计
     * @author Henfon
     * @date 2026-09-21
     */
    public AiRatingSummary summary(Long agentId) {
        List<AiRating> rows = list(agentId);
        long total = rows.size();
        if (total == 0) {
            return new AiRatingSummary(0L, null, 0L, 0L, List.of(0L, 0L, 0L, 0L, 0L));
        }
        long sum = 0L;
        long satisfied = 0L;
        long unsatisfied = 0L;
        long[] distribution = new long[AiRating.MAX_SCORE];
        for (AiRating rating : rows) {
            int score = rating.getScore() == null ? 0 : rating.getScore();
            sum += score;
            if (score >= 4) {
                satisfied++;
            }
            if (score > 0 && score <= 2) {
                unsatisfied++;
            }
            if (score >= 1 && score <= AiRating.MAX_SCORE) {
                distribution[score - 1]++;
            }
        }
        List<Long> buckets = new ArrayList<>(AiRating.MAX_SCORE);
        for (long count : distribution) {
            buckets.add(count);
        }
        double average = Math.round((double) sum / total * 10) / 10.0;
        return new AiRatingSummary(total, average, satisfied, unsatisfied, buckets);
    }

    /**
     * 最近的评价。
     *
     * @param agentId 只看某位客服，传 null 表示全部
     * @param limit 返回条数上限
     * @return 评价列表，时间倒序
     * @author Henfon
     * @date 2026-09-21
     */
    public List<AiRatingView> recent(Long agentId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return list(agentId).stream()
                .sorted(Comparator.comparing(AiRating::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(safeLimit)
                .map(AiRatingView::from)
                .toList();
    }

    /**
     * 读取评价明细。
     *
     * @param agentId 只看某位客服，传 null 表示全部
     * @return 评价列表
     * @author Henfon
     * @date 2026-09-21
     */
    private List<AiRating> list(Long agentId) {
        List<AiRating> rows = ratingMapper.selectList(Wrappers.lambdaQuery(AiRating.class)
                .eq(agentId != null, AiRating::getAgentId, agentId));
        return rows == null ? List.of() : rows;
    }

    /**
     * 按会话查询评价。
     *
     * @param conversationId 会话标识
     * @return 评价，不存在返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    private AiRating findByConversation(String conversationId) {
        return ratingMapper.selectOne(Wrappers.lambdaQuery(AiRating.class)
                .eq(AiRating::getConversationId, conversationId)
                .last("LIMIT 1"));
    }

    /**
     * 查询这批会话里已经评过的。
     *
     * @param conversationIds 会话标识列表
     * @return 已评价的会话标识集合
     * @author Henfon
     * @date 2026-09-21
     */
    private Set<String> ratedConversationIds(List<String> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Set.of();
        }
        List<AiRating> rows = ratingMapper.selectList(Wrappers.lambdaQuery(AiRating.class)
                .in(AiRating::getConversationId, conversationIds));
        Set<String> ids = new HashSet<>();
        if (rows != null) {
            rows.forEach(row -> ids.add(row.getConversationId()));
        }
        return ids;
    }

    /**
     * 拼装标签字符串。
     *
     * 去空白、去重、按顺序截断：标签由前端提供，长度与数量都不能信，超长标签会撑坏列表布局。
     *
     * @param tags 标签列表
     * @return 逗号分隔的标签，无有效标签时返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String tag : tags) {
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            unique.add(tag.trim());
            if (unique.size() >= MAX_TAGS) {
                break;
            }
        }
        return unique.isEmpty() ? null : String.join(",", unique);
    }
}
