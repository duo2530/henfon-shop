package com.henfon.shop.ai.dto;

import com.henfon.shop.ai.entity.AiRating;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 一条会话评价。
 *
 * 标签在库里是逗号分隔的字符串，对外返回数组：前端要按标签逐个渲染成小标签，
 * 让调用方自己去 split 迟早会出现"某个页面忘了去掉空白"这类不一致。
 *
 * @param conversationId 被评价的会话标识
 * @param memberId 评价人会员 ID
 * @param agentId 被评价的坐席 ID
 * @param agentName 坐席名称
 * @param score 评分，1-5 星
 * @param tags 评价标签
 * @param comment 评价留言
 * @param createdAt 评价时间
 * @author Henfon
 * @date 2026-09-21
 */
public record AiRatingView(String conversationId,
                           Long memberId,
                           Long agentId,
                           String agentName,
                           Integer score,
                           List<String> tags,
                           String comment,
                           LocalDateTime createdAt) {

    /**
     * 由实体构建。
     *
     * @param rating 评价实体
     * @return 视图
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiRatingView from(AiRating rating) {
        return new AiRatingView(rating.getConversationId(), rating.getMemberId(), rating.getAgentId(),
                rating.getAgentName(), rating.getScore(), splitTags(rating.getTags()),
                rating.getComment(), rating.getCreatedAt());
    }

    /**
     * 拆分标签字符串。
     *
     * @param raw 逗号分隔的标签
     * @return 标签列表，去空白并忽略空项
     * @author Henfon
     * @date 2026-09-21
     */
    static List<String> splitTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .toList();
    }
}
