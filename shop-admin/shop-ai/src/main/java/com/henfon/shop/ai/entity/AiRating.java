package com.henfon.shop.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人工会话满意度评价实体。
 *
 * 一条会话最多一条评价，由 conversation_id 唯一索引保证。没有评价记录不代表差评，
 * 评价率要用"已结束且有坐席的会话数"作分母另算。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Data
@TableName("ai_rating")
public class AiRating {

    /** 评分下限。 */
    public static final int MIN_SCORE = 1;

    /** 评分上限。 */
    public static final int MAX_SCORE = 5;

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 被评价的会话标识。 */
    private String conversationId;
    /** 评价人会员 ID。 */
    private Long memberId;
    /** 被评价的坐席 ID，从会话上带过来，便于按客服统计。 */
    private Long agentId;
    /** 坐席名称快照。 */
    private String agentName;
    /** 满意度评分：1-5 星。 */
    private Integer score;
    /** 评价标签，英文逗号分隔。 */
    private String tags;
    /** 评价留言，可空。 */
    private String comment;
    private LocalDateTime createdAt;
}
