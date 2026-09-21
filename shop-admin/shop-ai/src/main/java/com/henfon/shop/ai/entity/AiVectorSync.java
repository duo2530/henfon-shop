package com.henfon.shop.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 向量同步位点实体，记录每个来源对象的向量化结果与内容指纹。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Data
@TableName("ai_vector_sync")
public class AiVectorSync {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 向量来源类型：PRODUCT 商品，FAQ 问答，DOCUMENT 文档。 */
    private String sourceType;
    /** 来源记录标识，商品为商品 ID，FAQ 为问答 ID。 */
    private String sourceId;
    /** 写入的 Qdrant collection 名。 */
    private String collectionName;
    /** 向量化文本的 SHA-256，内容未变则跳过重新向量化。 */
    private String contentHash;
    /** Qdrant 中的点 ID，删除与覆盖时使用。 */
    private String pointId;
    /** 同步状态：PENDING 待同步，SYNCED 已同步，FAILED 失败。 */
    private String status;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
