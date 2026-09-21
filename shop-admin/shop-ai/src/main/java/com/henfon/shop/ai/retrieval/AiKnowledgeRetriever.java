package com.henfon.shop.ai.retrieval;

import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import com.henfon.shop.ai.config.AiProperties;
import com.henfon.shop.ai.dto.Ref;
import com.henfon.shop.ai.entity.AiFaq;
import com.henfon.shop.ai.mapper.AiFaqMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索。
 *
 * 流程是向量召回 top-K 交给百炼精排取 top-N，与方案里写的一致。两处取舍值得说明：
 *
 * 一是「命中与否」用向量相似度判断，不用精排得分。两者不是一个尺度——向量分是余弦
 * 相似度，精排分是相关性打分，共用一个阈值只会得到一个说不清含义的混合判据。是否
 * 命中影响的是"该不该告诉用户查不到"，这个判断必须稳定可解释，所以放在向量分上。
 *
 * 二是精排不可用时降级为直接取前 N 条，而不是报错。精排模型需要在百炼控制台单独开通，
 * 用户还没开通时对话不该整体不可用——召回质量下降是可接受的降级，答不出来不是。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Component
@ConditionalOnProperty(prefix = "shop.ai", name = "enabled", havingValue = "true")
public class AiKnowledgeRetriever {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeRetriever.class);

    // payload 里的 enabled 是布尔字段，而 Spring AI 的 Qdrant 过滤器转换器只接受字符串与
    // 数字，布尔值会抛 IllegalArgumentException，所以它不能出现在过滤条件里。停用知识也
    // 不靠它兜底：同步末尾的 removeStaleVectors() 会直接删掉来源已停用或下架的向量点。

    /** Qdrant payload 中的类目字段，已建索引。 */
    private static final String FIELD_CATEGORY = "category_code";

    /** Qdrant payload 中的标题字段。 */
    private static final String FIELD_TITLE = "title";

    /** Qdrant payload 中的来源类型字段。 */
    private static final String FIELD_SOURCE_TYPE = "source_type";

    /** Qdrant payload 中的来源标识字段。 */
    private static final String FIELD_SOURCE_ID = "source_id";

    /** 来源类型：知识库问答。问答的向量文本只有问法与关键词，答案要回表取。 */
    private static final String SOURCE_FAQ = "FAQ";

    private final AiProperties properties;

    private final VectorStore portalVectorStore;

    private final AiFaqMapper faqMapper;

    private final ObjectProvider<RerankModel> rerankModelProvider;

    /**
     * 创建知识库检索组件。
     *
     * @param properties AI 配置
     * @param portalVectorStore 门户 collection 的向量库
     * @param faqMapper 知识库问答 Mapper，用于补齐问答的答案正文
     * @param rerankModelProvider 百炼精排模型，未开通时为空
     * @author Henfon
     * @date 2026-09-21
     */
    public AiKnowledgeRetriever(AiProperties properties,
                                @Qualifier("portalVectorStore") VectorStore portalVectorStore,
                                AiFaqMapper faqMapper,
                                ObjectProvider<RerankModel> rerankModelProvider) {
        this.properties = properties;
        this.portalVectorStore = portalVectorStore;
        this.faqMapper = faqMapper;
        this.rerankModelProvider = rerankModelProvider;
    }

    /**
     * 检索门户公开知识。
     *
     * @param query 用户问题
     * @param categoryCode 类目编码，为空时不按类目过滤
     * @return 检索结果
     * @author Henfon
     * @date 2026-09-21
     */
    public RetrievalResult retrieve(String query, String categoryCode) {
        AiProperties.Retrieval retrieval = properties.getRetrieval();
        List<Document> documents = similaritySearch(query, categoryCode, retrieval.getTopK());
        if (documents.isEmpty()) {
            return new RetrievalResult(false, List.of(), "");
        }
        double threshold = retrieval.getScoreThreshold();
        double bestScore = documents.stream()
                .mapToDouble(document -> document.getScore() == null ? 0D : document.getScore())
                .max()
                .orElse(0D);
        List<RetrievedChunk> chunks = rerank(query, documents, retrieval.getRerankTopN());
        if (bestScore < threshold) {
            // 召回全都不相关时也把结果返回给上层，用于日志与引用展示，但按"未命中"处理，
            // 上层据此把回答收成"查不到"而不是让模型拿着无关资料硬答。
            log.info("知识库未命中：最高相似度 {}，阈值 {}，问题 {}", bestScore, threshold, abbreviate(query));
            return new RetrievalResult(false, chunks, "");
        }
        return new RetrievalResult(true, chunks, buildContext(chunks));
    }

    /**
     * 向量召回。
     *
     * 不把相似度下限交给向量库，而是召回后在本层判断：Qdrant 的 score_threshold 会把
     * 不达标的点直接过滤掉，返回空列表时无法区分"索引里没有"和"有关但不达标"，而这两种
     * 情况的日志与排查方向完全不同。
     *
     * @param query 用户问题
     * @param categoryCode 类目编码，可为空
     * @param topK 召回条数
     * @return 命中的文档，已带相似度
     * @author Henfon
     * @date 2026-09-21
     */
    private List<Document> similaritySearch(String query, String categoryCode, int topK) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(Math.max(1, topK))
                .similarityThresholdAll();
        Filter.Expression filter = buildFilter(categoryCode);
        if (filter != null) {
            builder.filterExpression(filter);
        }
        SearchRequest request = builder.build();
        try {
            return portalVectorStore.similaritySearch(request);
        } catch (Exception exception) {
            // 向量库不可达或集群被闲置暂停都会走到这里。上层会拿到空结果并降级为转人工，
            // 不让整个对话接口因为检索失败而报错。
            log.warn("向量检索失败：{}", exception.getMessage());
            return List.of();
        }
    }

    /**
     * 组装过滤条件。
     *
     * 只按类目过滤，且类目为空时不带任何条件。这里踩过两个坑：转换器只认字符串与数字，
     * 布尔值会在本地抛 IllegalArgumentException，异常被上层 catch 后静默降级成空结果，
     * 表面看只是"没命中"，很容易误判成知识库没灌；strict mode 又要求过滤字段必须先在
     * 控制台建索引，否则被服务端直接拒绝。
     *
     * @param categoryCode 类目编码，可为空
     * @return 过滤表达式，类目为空时返回 null 表示不带过滤
     * @author Henfon
     * @date 2026-09-21
     */
    private Filter.Expression buildFilter(String categoryCode) {
        if (!StringUtils.hasText(categoryCode)) {
            return null;
        }
        return new FilterExpressionBuilder().eq(FIELD_CATEGORY, categoryCode).build();
    }

    /**
     * 调用百炼精排重新排序。
     *
     * @param query 用户问题
     * @param documents 召回的文档
     * @param topN 保留条数
     * @return 精排后的结果，精排不可用时退化为按向量分排序取前 N 条
     * @author Henfon
     * @date 2026-09-21
     */
    private List<RetrievedChunk> rerank(String query, List<Document> documents, int topN) {
        int limit = Math.max(1, topN);
        RerankModel rerankModel = rerankModelProvider.getIfAvailable();
        if (rerankModel == null) {
            log.debug("精排模型未装配，按向量相似度取前 {} 条", limit);
            return documents.stream().limit(limit).map(this::toChunk).toList();
        }
        try {
            DashScopeRerankOptions options = DashScopeRerankOptions.builder()
                    .withModel(properties.getChat().getRerankModel())
                    .withTopN(limit)
                    .withReturnDocuments(true)
                    .build();
            RerankResponse response = rerankModel.call(new RerankRequest(query, documents, options));
            List<RetrievedChunk> chunks = new ArrayList<>();
            response.getResults().stream().limit(limit).forEach(result ->
                    chunks.add(toChunk(result.getOutput(), result.getScore())));
            if (chunks.isEmpty()) {
                return documents.stream().limit(limit).map(this::toChunk).toList();
            }
            return chunks;
        } catch (Exception exception) {
            log.warn("精排失败，降级为向量排序：{}", exception.getMessage());
            return documents.stream().limit(limit).map(this::toChunk).toList();
        }
    }

    /**
     * 把文档转成检索结果。
     *
     * @param document 命中的文档
     * @return 检索结果
     * @author Henfon
     * @date 2026-09-21
     */
    private RetrievedChunk toChunk(Document document) {
        return toChunk(document, null);
    }

    /**
     * 把文档转成检索结果并带上精排得分。
     *
     * @param document 命中的文档
     * @param rerankScore 精排得分，可为空
     * @return 检索结果
     * @author Henfon
     * @date 2026-09-21
     */
    private RetrievedChunk toChunk(Document document, Double rerankScore) {
        if (document == null) {
            return new RetrievedChunk(null, null, null, "", 0D, rerankScore);
        }
        return new RetrievedChunk(
                stringMetadata(document, FIELD_SOURCE_TYPE),
                stringMetadata(document, FIELD_SOURCE_ID),
                stringMetadata(document, FIELD_TITLE),
                document.getText() == null ? "" : document.getText(),
                document.getScore() == null ? 0D : document.getScore(),
                rerankScore);
    }

    /**
     * 读取文档元数据中的字符串字段。
     *
     * @param document 文档
     * @param key 键名
     * @return 字段值，不存在时返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    private String stringMetadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 把检索结果拼成给模型的资料段落。
     *
     * 问答条目的向量文本只有问法与关键词，答案正文不在向量库里（答案参与向量化会让答案里
     * 出现过的词反过来变成召回依据）。所以这里要按 source_id 回表取答案，否则模型看到的
     * 只是「有这么一个问题」，没有任何可依据的事实——它会顺手把关键词当成平台规则，
     * 甚至用常识补齐支付方式、页面入口这类并不存在的内容。
     *
     * @param chunks 检索结果
     * @return 资料文本
     * @author Henfon
     * @date 2026-09-21
     */
    private String buildContext(List<RetrievedChunk> chunks) {
        StringBuilder context = new StringBuilder();
        Map<String, String> faqCache = new HashMap<>();
        int index = 0;
        for (RetrievedChunk chunk : chunks) {
            String text = contextText(chunk, faqCache);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            index++;
            context.append("资料").append(index).append('（').append(chunk.label()).append("）：")
                    .append(text).append('\n');
        }
        return context.toString();
    }

    /**
     * 取单条检索结果用于拼上下文的正文。
     *
     * @param chunk 检索结果
     * @param faqCache 本次检索内已取过的问答答案，同一问答被多次召回时不重复查库
     * @return 正文，问答回表失败时退回问法本身
     * @author Henfon
     * @date 2026-09-21
     */
    private String contextText(RetrievedChunk chunk, Map<String, String> faqCache) {
        if (!SOURCE_FAQ.equals(chunk.sourceType()) || !StringUtils.hasText(chunk.sourceId())) {
            return chunk.text();
        }
        return faqCache.computeIfAbsent(chunk.sourceId(), id -> loadFaqText(id, chunk.text()));
    }

    /**
     * 读取问答的标准问法与标准答案。
     *
     * @param sourceId 问答 ID
     * @param fallback 取不到时的回退文本
     * @return 问法与答案拼成的正文
     * @author Henfon
     * @date 2026-09-21
     */
    private String loadFaqText(String sourceId, String fallback) {
        try {
            AiFaq faq = faqMapper.selectById(Long.parseLong(sourceId));
            // 来源已删除或已停用时退回问法：向量点由同步末尾的 removeStaleVectors() 清理，
            // 在那之前宁可少给一条资料，也不要让一条作废的答案继续被引用。
            if (faq == null || !Integer.valueOf(1).equals(faq.getEnabled())) {
                return fallback;
            }
            return "问：" + faq.getQuestion() + "\n答：" + faq.getAnswer();
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /**
     * 截断日志中的问题文本。
     *
     * @param text 原始文本
     * @return 截断后的文本
     * @author Henfon
     * @date 2026-09-21
     */
    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 40 ? text : text.substring(0, 40) + "…";
    }

    /**
     * 一次检索的结果。
     *
     * @param hit 是否命中，未命中时上层只能回答"查不到"并要求转人工
     * @param chunks 召回并精排后的条目，未命中时也可能有值，仅用于展示引用
     * @param context 拼好的资料文本，未命中时为空串
     * @author Henfon
     * @date 2026-09-21
     */
    public record RetrievalResult(boolean hit, List<RetrievedChunk> chunks, String context) {

        /**
         * 转成给前端展示的引用列表。
         *
         * @return 引用列表
         * @author Henfon
         * @date 2026-09-21
         */
        public List<Ref> toRefs() {
            return chunks.stream()
                    .filter(chunk -> StringUtils.hasText(chunk.text()))
                    .map(chunk -> new Ref(chunk.sourceType(), chunk.sourceId(), chunk.title()))
                    .toList();
        }
    }
}
