package com.henfon.shop.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 客服知识库问答实体。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Data
@TableName("ai_faq")
public class AiFaq {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 标准问法，同时作为向量化文本的来源。 */
    private String question;
    /** 标准答案，命中后拼进模型上下文。 */
    private String answer;
    /** 业务分类，见 ai_faq.category 列注释。 */
    private String category;
    /** 人工维护的检索关键词，英文逗号分隔。 */
    private String keywords;
    private Integer sortNo;
    /** 是否启用：1启用，0停用。 */
    private Integer enabled;
    /** 向量同步状态：PENDING/SYNCED/FAILED。 */
    private String syncStatus;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
}
