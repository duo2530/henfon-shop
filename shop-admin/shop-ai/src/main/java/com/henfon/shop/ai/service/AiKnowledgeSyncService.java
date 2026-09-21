package com.henfon.shop.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.henfon.shop.ai.config.AiProperties;
import com.henfon.shop.ai.entity.AiFaq;
import com.henfon.shop.ai.entity.AiVectorSync;
import com.henfon.shop.ai.mapper.AiFaqMapper;
import com.henfon.shop.ai.mapper.AiVectorSyncMapper;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.entity.CatalogProductSpec;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.catalog.mapper.CatalogProductSpecMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库向量同步服务。
 *
 * 把商品与 FAQ 组装成可检索文本，分批送百炼向量化后写入门户 collection，并在
 * ai_vector_sync 上记录内容指纹：文本没变就整批跳过，避免重复消耗 embedding 额度。
 *
 * 两条约定贯穿全类：一是分批（百炼 text-embedding-v4 单次最多 10 条），二是单批失败
 * 不阻塞整体——失败批只记 error_message 与 retry_count，其余批次照常推进，下次重跑
 * 会自动带上失败的条目。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Service
@ConditionalOnProperty(prefix = "shop.ai", name = "enabled", havingValue = "true")
public class AiKnowledgeSyncService {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeSyncService.class);

    /** 来源类型：商品。 */
    private static final String SOURCE_PRODUCT = "PRODUCT";

    /** 来源类型：知识库问答。 */
    private static final String SOURCE_FAQ = "FAQ";

    private static final String STATUS_SYNCED = "SYNCED";

    private static final String STATUS_FAILED = "FAILED";

    /** 商品简介截断长度，过长的详情页文案对检索没有增益，只会稀释向量。 */
    private static final int DESCRIPTION_LIMIT = 300;

    private final AiProperties properties;

    private final CatalogProductMapper productMapper;

    private final CatalogProductSpecMapper productSpecMapper;

    private final AiFaqMapper faqMapper;

    private final AiVectorSyncMapper vectorSyncMapper;

    private final VectorStore portalVectorStore;

    /**
     * 创建知识库向量同步服务。
     *
     * @param properties AI 配置
     * @param productMapper 商品 Mapper
     * @param productSpecMapper 商品参数 Mapper
     * @param faqMapper 知识库问答 Mapper
     * @param vectorSyncMapper 向量同步位点 Mapper
     * @param portalVectorStore 门户 collection 的向量库
     * @author Henfon
     * @date 2026-09-21
     */
    public AiKnowledgeSyncService(AiProperties properties,
                                  CatalogProductMapper productMapper,
                                  CatalogProductSpecMapper productSpecMapper,
                                  AiFaqMapper faqMapper,
                                  AiVectorSyncMapper vectorSyncMapper,
                                  @Qualifier("portalVectorStore") VectorStore portalVectorStore) {
        this.properties = properties;
        this.productMapper = productMapper;
        this.productSpecMapper = productSpecMapper;
        this.faqMapper = faqMapper;
        this.vectorSyncMapper = vectorSyncMapper;
        this.portalVectorStore = portalVectorStore;
    }

    /**
     * 同步上架商家的商品向量。
     *
     * @param force 为 true 时忽略内容指纹强制重建，用于调整文本组装策略后的全量重跑
     * @return 同步结果统计
     * @author Henfon
     * @date 2026-09-21
     */
    public SyncResult syncProducts(boolean force) {
        LambdaQueryWrapper<CatalogProduct> wrapper = Wrappers.lambdaQuery(CatalogProduct.class)
                // 只索引上架商品：下架商品答不出来才符合预期，索引了反而会答出买不到的东西。
                .eq(CatalogProduct::getStatus, 1)
                .orderByAsc(CatalogProduct::getId);
        int limit = properties.getVectorize().getMaxItems();
        if (limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        List<CatalogProduct> products = productMapper.selectList(wrapper);
        if (products.isEmpty()) {
            return new SyncResult(0, 0, 0, 0);
        }
        Map<Long, List<CatalogProductSpec>> specMap = loadSpecs(products);
        return syncBatch(SOURCE_PRODUCT, products,
                product -> String.valueOf(product.getId()),
                product -> buildProductText(product, specMap.getOrDefault(product.getId(), List.of())),
                force);
    }

    /**
     * 同步启用中的知识库问答。
     *
     * @param force 为 true 时忽略内容指纹强制重建
     * @return 同步结果统计
     * @author Henfon
     * @date 2026-09-21
     */
    public SyncResult syncFaqs(boolean force) {
        List<AiFaq> faqs = faqMapper.selectList(Wrappers.lambdaQuery(AiFaq.class)
                .eq(AiFaq::getEnabled, 1)
                .orderByAsc(AiFaq::getSortNo)
                .orderByAsc(AiFaq::getId));
        if (faqs.isEmpty()) {
            return new SyncResult(0, 0, 0, 0);
        }
        return syncBatch(SOURCE_FAQ, faqs,
                faq -> String.valueOf(faq.getId()),
                this::buildFaqText,
                force);
    }

    /**
     * 清理来源已不存在的向量点。
     *
     * 商品下架、FAQ 删除或停用后，位点仍在库里。不清理的后果是召回出已经买不到或已经
     * 作废的内容，比答不出来更糟，所以每次全量同步顺带收敛一次。
     *
     * @return 被清理的点数量
     * @author Henfon
     * @date 2026-09-21
     */
    public int removeStaleVectors() {
        String collection = properties.getPortalCollection();
        List<AiVectorSync> points = vectorSyncMapper.selectList(Wrappers.lambdaQuery(AiVectorSync.class)
                .eq(AiVectorSync::getCollectionName, collection));
        if (points.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (AiVectorSync point : points) {
            if (!isSourceAlive(point.getSourceType(), point.getSourceId())) {
                try {
                    portalVectorStore.delete(List.of(point.getPointId()));
                    vectorSyncMapper.deleteById(point.getId());
                    removed++;
                } catch (Exception exception) {
                    log.warn("清理失效向量点失败：{} {} {}", point.getSourceType(), point.getSourceId(),
                            exception.getMessage());
                }
            }
        }
        return removed;
    }

    /**
     * 判断来源记录是否仍然有效。
     *
     * @param sourceType 来源类型
     * @param sourceId 来源标识
     * @return 仍然有效返回 true
     * @author Henfon
     * @date 2026-09-21
     */
    private boolean isSourceAlive(String sourceType, String sourceId) {
        try {
            long id = Long.parseLong(sourceId);
            if (SOURCE_PRODUCT.equals(sourceType)) {
                CatalogProduct product = productMapper.selectById(id);
                return product != null && Integer.valueOf(1).equals(product.getStatus());
            }
            if (SOURCE_FAQ.equals(sourceType)) {
                AiFaq faq = faqMapper.selectById(id);
                return faq != null && Integer.valueOf(1).equals(faq.getEnabled());
            }
            return true;
        } catch (NumberFormatException exception) {
            // 来源标识不是数字说明是历史脏数据，删除比留在库里安全。
            return false;
        }
    }

    /**
     * 按批向量化并写入向量库。
     *
     * @param sourceType 来源类型
     * @param sources 来源对象列表
     * @param idGetter 取来源标识
     * @param textBuilder 取向量化文本
     * @param force 是否强制重建
     * @param <T> 来源对象类型
     * @return 同步结果统计
     * @author Henfon
     * @date 2026-09-21
     */
    private <T> SyncResult syncBatch(String sourceType,
                                     List<T> sources,
                                     Function<T, String> idGetter,
                                     Function<T, String> textBuilder,
                                     boolean force) {
        int batchSize = Math.max(1, properties.getVectorize().getBatchSize());
        int maxRetries = Math.max(1, properties.getVectorize().getMaxRetries());
        long throttleMs = properties.getVectorize().getThrottleMs();
        String collection = properties.getPortalCollection();

        int success = 0;
        int skipped = 0;
        int failed = 0;
        List<Document> batch = new ArrayList<>(batchSize);
        List<PendingPoint> pending = new ArrayList<>(batchSize);

        for (T source : sources) {
            String sourceId = idGetter.apply(source);
            String text = textBuilder.apply(source);
            String hash = sha256(text);
            AiVectorSync existing = findPoint(sourceType, sourceId, collection);
            if (!force && existing != null && hash.equals(existing.getContentHash())
                    && STATUS_SYNCED.equals(existing.getStatus())) {
                skipped++;
                continue;
            }
            String pointId = existing != null && existing.getPointId() != null
                    ? existing.getPointId()
                    : pointId(sourceType, sourceId);
            batch.add(Document.builder()
                    .id(pointId)
                    .text(text)
                    .metadata(buildMetadata(sourceType, sourceId, source))
                    .build());
            pending.add(new PendingPoint(existing, sourceId, pointId, hash));
            if (batch.size() >= batchSize) {
                if (flush(batch, pending, sourceType, collection, maxRetries)) {
                    success += batch.size();
                } else {
                    failed += batch.size();
                }
                batch.clear();
                pending.clear();
                sleep(throttleMs);
            }
        }
        if (!batch.isEmpty()) {
            if (flush(batch, pending, sourceType, collection, maxRetries)) {
                success += batch.size();
            } else {
                failed += batch.size();
            }
        }
        log.info("向量同步完成：来源 {}，成功 {}，跳过 {}，失败 {}", sourceType, success, skipped, failed);
        return new SyncResult(sources.size(), success, skipped, failed);
    }

    /**
     * 提交一批向量并回写同步位点。
     *
     * @param batch 本批文档
     * @param pending 本批位点信息
     * @param sourceType 来源类型
     * @param collection collection 名
     * @param maxRetries 最大重试次数
     * @return 全部成功返回 true
     * @author Henfon
     * @date 2026-09-21
     */
    private boolean flush(List<Document> batch,
                          List<PendingPoint> pending,
                          String sourceType,
                          String collection,
                          int maxRetries) {
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                portalVectorStore.add(new ArrayList<>(batch));
                for (PendingPoint point : pending) {
                    markSynced(point, sourceType, collection);
                }
                return true;
            } catch (Exception exception) {
                lastError = exception;
                log.warn("向量写入失败（第 {} 次，本批 {} 条）：{}", attempt, batch.size(), exception.getMessage());
                // 服务端限流或瞬时网络问题居多，退避后再试；每次重试都拉开间隔。
                sleep(properties.getVectorize().getThrottleMs() * attempt);
            }
        }
        for (PendingPoint point : pending) {
            markFailed(point, sourceType, collection, lastError);
        }
        return false;
    }

    /**
     * 记录同步成功。
     *
     * @param point 位点信息
     * @param sourceType 来源类型
     * @param collection collection 名
     * @author Henfon
     * @date 2026-09-21
     */
    private void markSynced(PendingPoint point, String sourceType, String collection) {
        AiVectorSync entity = point.existing != null ? point.existing : new AiVectorSync();
        entity.setSourceType(sourceType);
        entity.setSourceId(point.sourceId);
        entity.setCollectionName(collection);
        entity.setContentHash(point.hash);
        entity.setPointId(point.pointId);
        entity.setStatus(STATUS_SYNCED);
        entity.setRetryCount(0);
        entity.setErrorMessage(null);
        entity.setSyncedAt(LocalDateTime.now());
        if (entity.getId() == null) {
            vectorSyncMapper.insert(entity);
        } else {
            vectorSyncMapper.updateById(entity);
        }
    }

    /**
     * 记录同步失败。
     *
     * @param point 位点信息
     * @param sourceType 来源类型
     * @param collection collection 名
     * @param error 失败原因
     * @author Henfon
     * @date 2026-09-21
     */
    private void markFailed(PendingPoint point, String sourceType, String collection, Exception error) {
        AiVectorSync entity = point.existing != null ? point.existing : new AiVectorSync();
        entity.setSourceType(sourceType);
        entity.setSourceId(point.sourceId);
        entity.setCollectionName(collection);
        entity.setContentHash(point.hash);
        entity.setPointId(point.pointId);
        entity.setStatus(STATUS_FAILED);
        entity.setRetryCount(entity.getRetryCount() == null ? 1 : entity.getRetryCount() + 1);
        String message = error == null ? "未知错误" : error.getMessage();
        entity.setErrorMessage(message == null ? null : message.substring(0, Math.min(500, message.length())));
        if (entity.getId() == null) {
            vectorSyncMapper.insert(entity);
        } else {
            vectorSyncMapper.updateById(entity);
        }
    }

    /**
     * 查询同步位点。
     *
     * @param sourceType 来源类型
     * @param sourceId 来源标识
     * @param collection collection 名
     * @return 位点记录，不存在返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    private AiVectorSync findPoint(String sourceType, String sourceId, String collection) {
        return vectorSyncMapper.selectOne(Wrappers.lambdaQuery(AiVectorSync.class)
                .eq(AiVectorSync::getSourceType, sourceType)
                .eq(AiVectorSync::getSourceId, sourceId)
                .eq(AiVectorSync::getCollectionName, collection)
                .last("LIMIT 1"));
    }

    /**
     * 组装商品的可检索文本。
     *
     * 写成一段自然语言而不是把整行 JSON 丢进去：向量模型对"120 厘米宽的置物架"这类
     * 描述性文本的语义捕捉，比对字段名与字段值拼接的 JSON 好得多。买家提问是自然语言，
     * 入库文本也应当是自然语言，两边形态一致才有可比性。
     *
     * @param product 商品
     * @param specs 商品参数
     * @return 可检索文本
     * @author Henfon
     * @date 2026-09-21
     */
    private String buildProductText(CatalogProduct product, List<CatalogProductSpec> specs) {
        StringBuilder text = new StringBuilder();
        text.append(product.getProductName() == null ? "" : product.getProductName());
        if (StringUtils.hasText(product.getBrandName())) {
            text.append("，品牌：").append(product.getBrandName());
        }
        if (StringUtils.hasText(product.getCategoryName())) {
            text.append("，所属类目：").append(product.getCategoryName());
        }
        if (StringUtils.hasText(product.getShortDescription())) {
            text.append("。卖点：").append(product.getShortDescription());
        }
        if (!specs.isEmpty()) {
            String specText = specs.stream()
                    .filter(spec -> StringUtils.hasText(spec.getSpecName()))
                    .map(spec -> spec.getSpecName() + "为" + spec.getSpecValue())
                    .collect(Collectors.joining("，"));
            if (StringUtils.hasText(specText)) {
                text.append("。规格参数：").append(specText);
            }
        }
        if (StringUtils.hasText(product.getTagsCsv())) {
            text.append("。标签：").append(product.getTagsCsv().replace(",", "、"));
        }
        if (StringUtils.hasText(product.getDescription())) {
            String description = product.getDescription().replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            if (description.length() > DESCRIPTION_LIMIT) {
                description = description.substring(0, DESCRIPTION_LIMIT);
            }
            if (StringUtils.hasText(description)) {
                text.append("。商品说明：").append(description);
            }
        }
        return text.toString();
    }

    /**
     * 组装知识库问答的可检索文本。
     *
     * 问法与关键词都进文本，答案不进：答案是要拼给模型的原文，把它也向量化会让
     * "答案里出现过的词"反过来变成召回依据，容易把不相干的问答拉进来。
     *
     * 答案由 AiKnowledgeRetriever 在召回后按 source_id 回表取，不走向量库。所以改答案
     * 不需要重新同步——改问法或关键词才需要。
     *
     * @param faq 知识库问答
     * @return 可检索文本
     * @author Henfon
     * @date 2026-09-21
     */
    private String buildFaqText(AiFaq faq) {
        StringBuilder text = new StringBuilder(faq.getQuestion() == null ? "" : faq.getQuestion());
        if (StringUtils.hasText(faq.getKeywords())) {
            text.append("。相关说法：").append(faq.getKeywords().replace(",", "、"));
        }
        return text.toString();
    }

    /**
     * 组装写入向量库的 payload。
     *
     * 键名必须与 Qdrant 上已建索引的字段一致（source_type、category_code、enabled）：
     * 集群开着 strict mode，未建索引的字段一旦出现在过滤条件里会被服务端直接拒绝。
     *
     * @param sourceType 来源类型
     * @param sourceId 来源标识
     * @param source 来源对象
     * @return payload
     * @author Henfon
     * @date 2026-09-21
     */
    private Map<String, Object> buildMetadata(String sourceType, String sourceId, Object source) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source_type", sourceType);
        metadata.put("source_id", sourceId);
        metadata.put("enabled", true);
        String categoryCode = null;
        String title = null;
        if (source instanceof CatalogProduct product) {
            categoryCode = product.getCategoryId() == null ? null : String.valueOf(product.getCategoryId());
            title = product.getProductName();
        } else if (source instanceof AiFaq faq) {
            categoryCode = faq.getCategory();
            title = faq.getQuestion();
        }
        // 类目为空时给一个占位值，避免过滤条件里出现 null 造成检索语义混乱。
        metadata.put("category_code", categoryCode == null ? "UNCLASSIFIED" : categoryCode);
        metadata.put("title", title == null ? "" : title);
        return metadata;
    }

    /**
     * 生成稳定的向量点标识。
     *
     * 用来源类型与来源 ID 派生 UUID，同一个商品重复同步落到同一个点上，天然覆盖旧向量，
     * 不需要先查再删。
     *
     * @param sourceType 来源类型
     * @param sourceId 来源标识
     * @return UUID 形式的点标识
     * @author Henfon
     * @date 2026-09-21
     */
    private String pointId(String sourceType, String sourceId) {
        return UUID.nameUUIDFromBytes((sourceType + ":" + sourceId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * 计算文本的 SHA-256 指纹。
     *
     * @param text 文本
     * @return 十六进制指纹
     * @author Henfon
     * @date 2026-09-21
     */
    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            // JDK 必然提供 SHA-256，走到这里说明运行环境异常，用回退值让流程继续而不是中断同步。
            return "fallback-" + Integer.toHexString(text.hashCode());
        }
    }

    /**
     * 批量加载商品参数，避免逐条查询。
     *
     * @param products 商品列表
     * @return 商品 ID 到参数列表的映射
     * @author Henfon
     * @date 2026-09-21
     */
    private Map<Long, List<CatalogProductSpec>> loadSpecs(List<CatalogProduct> products) {
        List<Long> productIds = products.stream().map(CatalogProduct::getId).toList();
        List<CatalogProductSpec> specs = productSpecMapper.selectList(Wrappers.lambdaQuery(CatalogProductSpec.class)
                .in(CatalogProductSpec::getProductId, productIds)
                .orderByAsc(CatalogProductSpec::getSortNo));
        return specs.stream().collect(Collectors.groupingBy(CatalogProductSpec::getProductId));
    }

    /**
     * 休眠指定毫秒数。
     *
     * @param millis 毫秒数
     * @author Henfon
     * @date 2026-09-21
     */
    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 向量同步结果。
     *
     * @param total 参与本次同步的来源总数
     * @param success 成功写入的条数
     * @param skipped 因内容未变而跳过的条数
     * @param failed 写入失败的条数
     * @author Henfon
     * @date 2026-09-21
     */
    public record SyncResult(int total, int success, int skipped, int failed) {
    }

    /**
     * 待写入位点信息。
     *
     * @param existing 已存在的位点记录
     * @param sourceId 来源标识
     * @param pointId 向量点标识
     * @param hash 内容指纹
     * @author Henfon
     * @date 2026-09-21
     */
    private record PendingPoint(AiVectorSync existing, String sourceId, String pointId, String hash) {
    }
}
