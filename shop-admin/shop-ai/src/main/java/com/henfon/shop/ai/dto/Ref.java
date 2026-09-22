package com.henfon.shop.ai.dto;

/**
 * 回答引用的知识来源。
 *
 * 只带来源标识与标题，不带原文：买家侧要看到的是"答案出自哪条规则"，把整段召回原文推给
 * 前端会让客服窗口变成半个知识库页面，也容易把本该留在服务端的资料拼装细节暴露出去。
 *
 * @param sourceType 来源类型：PRODUCT 商品，FAQ 知识库问答，DOCUMENT 文档
 * @param sourceId 来源记录标识
 * @param title 来源标题
 * @author Henfon
 * @date 2026-09-21
 */
public record Ref(String sourceType, String sourceId, String title) {

    /** 商品来源类型，与向量库 source_type 的取值一致。 */
    public static final String SOURCE_PRODUCT = "PRODUCT";
}
