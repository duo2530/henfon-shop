package com.henfon.shop.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 知识文档实体，P2 阶段的知识库运营使用。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Data
@TableName("ai_document")
public class AiDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    /** 文档类型：POLICY 政策，SOP 流程，OTHER 其他。 */
    private String docType;
    /** 原文来源，MinIO 对象键或外部链接。 */
    private String source;
    /** 文档版本号，由运营人工维护；乐观锁沿用 version 列，故此处另名。 */
    private String docVersion;
    /** 索引状态：DRAFT 草稿，INDEXED 已索引，FAILED 索引失败。 */
    private String status;
    private Integer chunkCount;
    private String errorMessage;
    private LocalDateTime indexedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
}
