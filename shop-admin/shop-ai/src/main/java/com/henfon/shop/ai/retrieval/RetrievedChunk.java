package com.henfon.shop.ai.retrieval;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 一条检索结果。
 *
 * 除了拼进上下文的正文，还带上来源信息：回答要能说清依据出自哪条资料，运营排查"为什么
 * 答错了"时，看的就是这一组召回结果。
 *
 * @param sourceType 来源类型：PRODUCT 商品，FAQ 知识库问答，DOCUMENT 文档
 * @param sourceId 来源记录标识
 * @param title 来源标题，商品为商品名，问答为标准问法
 * @param text 命中的正文
 * @param vectorScore 向量召回的相似度
 * @param rerankScore 精排得分，未经精排时为空
 * @author Henfon
 * @date 2026-09-21
 */
public record RetrievedChunk(String sourceType,
                             String sourceId,
                             String title,
                             String text,
                             double vectorScore,
                             Double rerankScore) {

    /**
     * 生成资料在上下文中的标题行。
     *
     * 标注成 JSON 属性是给召回测试接口用的：它把这条记录直接下发给知识库界面，前端要显示
     * 「商品：实木置物架」这一行。不加这个注解，记录序列化只输出构造参数，label 会丢掉。
     *
     * @return 形如「商品：实木置物架」的标题
     * @author Henfon
     * @date 2026-09-21
     */
    @JsonProperty("label")
    public String label() {
        return switch (sourceType == null ? "" : sourceType) {
            case "PRODUCT" -> "商品";
            case "FAQ" -> "平台问答";
            case "DOCUMENT" -> "平台规则";
            default -> "资料";
        } + "：" + (title == null || title.isBlank() ? sourceId : title);
    }
}
