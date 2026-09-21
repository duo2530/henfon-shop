package com.henfon.shop.ai.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.henfon.shop.ai.dto.AiRecallTestRequest;
import com.henfon.shop.ai.entity.AiFaq;
import com.henfon.shop.ai.entity.AiVectorSync;
import com.henfon.shop.ai.mapper.AiFaqMapper;
import com.henfon.shop.ai.mapper.AiVectorSyncMapper;
import com.henfon.shop.ai.retrieval.AiKnowledgeRetriever;
import com.henfon.shop.ai.service.AiKnowledgeSyncService;
import com.henfon.shop.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库向量同步与召回测试接口。
 *
 * 同步是耗时操作（几百个商品要分批调百炼），放在管理端由运营手动触发，不做定时全量
 * 重跑：embedding 是按量计费的，静默的定时任务容易在没人看的时候把额度烧掉。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@RestController
@RequestMapping("/api/admin/ai/knowledge")
public class AiKnowledgeAdminController {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeAdminController.class);

    private final ObjectProvider<AiKnowledgeSyncService> syncServiceProvider;

    private final ObjectProvider<AiKnowledgeRetriever> retrieverProvider;

    private final AiVectorSyncMapper vectorSyncMapper;

    private final AiFaqMapper faqMapper;

    /**
     * 创建知识库同步控制器。
     *
     * @param syncServiceProvider 向量同步服务，AI 未启用时为空
     * @param retrieverProvider 知识库检索组件，AI 未启用时为空
     * @param vectorSyncMapper 向量同步位点 Mapper
     * @param faqMapper 知识库问答 Mapper
     * @author Henfon
     * @date 2026-09-21
     */
    public AiKnowledgeAdminController(ObjectProvider<AiKnowledgeSyncService> syncServiceProvider,
                                      ObjectProvider<AiKnowledgeRetriever> retrieverProvider,
                                      AiVectorSyncMapper vectorSyncMapper,
                                      AiFaqMapper faqMapper) {
        this.syncServiceProvider = syncServiceProvider;
        this.retrieverProvider = retrieverProvider;
        this.vectorSyncMapper = vectorSyncMapper;
        this.faqMapper = faqMapper;
    }

    /**
     * 同步商品与问答的向量。
     *
     * @param force 为 true 时忽略内容指纹强制重建
     * @return 同步结果
     * @author Henfon
     * @date 2026-09-21
     */
    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('ai:knowledge:sync')")
    public ApiResponse<Map<String, Object>> sync(@RequestParam(defaultValue = "false") boolean force) {
        AiKnowledgeSyncService syncService = syncServiceProvider.getIfAvailable();
        if (syncService == null) {
            return ApiResponse.failure("AI_DISABLED", "AI 客服未启用，请先配置百炼与 Qdrant 并打开开关", requestId());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("product", syncService.syncProducts(force));
        result.put("faq", syncService.syncFaqs(force));
        result.put("staleRemoved", syncService.removeStaleVectors());
        return ApiResponse.success(result, requestId());
    }

    /**
     * 查询向量同步概况。
     *
     * 位点表的规模就是知识条数（四百上下），一次取回在内存里分组即可：三条 count 换不来什么
     * 性能，倒是能顺手把「哪一类来源卡住了」与失败明细一起给出去——运营看到 failed=3 时，
     * 下一个问题必然是「哪三条、报的什么错」。
     *
     * @return 同步统计与失败明细
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping("/status")
    @PreAuthorize("hasAuthority('ai:knowledge:sync')")
    public ApiResponse<Map<String, Object>> status() {
        List<AiVectorSync> points = vectorSyncMapper.selectList(Wrappers.lambdaQuery(AiVectorSync.class)
                .orderByDesc(AiVectorSync::getId));
        Map<String, Map<String, Long>> bySource = new LinkedHashMap<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        long synced = 0;
        long failed = 0;
        for (AiVectorSync point : points) {
            String status = point.getStatus() == null ? "UNKNOWN" : point.getStatus();
            switch (status) {
                case "SYNCED" -> synced++;
                case "FAILED" -> failed++;
                default -> log.debug("向量位点 {} 的状态为 {}，不计入统计", point.getId(), status);
            }
            String sourceType = point.getSourceType() == null ? "UNKNOWN" : point.getSourceType();
            bySource.computeIfAbsent(sourceType, key -> new LinkedHashMap<>())
                    .merge(status, 1L, Long::sum);
            if ("FAILED".equals(status)) {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("id", point.getId());
                failure.put("sourceType", sourceType);
                failure.put("sourceId", point.getSourceId());
                failure.put("retryCount", point.getRetryCount());
                failure.put("errorMessage", point.getErrorMessage());
                failure.put("updatedAt", point.getUpdatedAt());
                failures.add(failure);
            }
        }
        // 「待同步」取源表而不是位点表：位点是在向量写成功之后才落的，它的 PENDING 正常不会出现，
        // 拿它当待同步数永远是 0。运营真正要问的是「有几条改过还没重新索引」，那写在
        // ai_faq.sync_status 上。商品没有这个字段，它的改动由下次同步按内容指纹自行发现。
        long pending = faqMapper.selectCount(Wrappers.lambdaQuery(AiFaq.class)
                .eq(AiFaq::getSyncStatus, "PENDING")
                .eq(AiFaq::getEnabled, 1));
        bySource.computeIfAbsent("FAQ", key -> new LinkedHashMap<>()).put("PENDING", pending);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", (long) points.size());
        result.put("synced", synced);
        result.put("pending", pending);
        result.put("failed", failed);
        result.put("bySource", bySource);
        // 失败明细只给前 50 条：界面是拿来定位问题的，不是拿来做报表的。
        result.put("failures", failures.size() > 50 ? failures.subList(0, 50) : failures);
        result.put("enabled", syncServiceProvider.getIfAvailable() != null);
        return ApiResponse.success(result, requestId());
    }

    /**
     * 重试同步失败的条目。
     *
     * 只重跑失败来源，不碰已经同步好的数据，避免为几百条正常记录重复付 embedding 费用。
     *
     * @return 本次重试的同步统计
     * @author Henfon
     * @date 2026-09-21
     */
    @PostMapping("/retry-failed")
    @PreAuthorize("hasAuthority('ai:knowledge:sync')")
    public ApiResponse<Map<String, Object>> retryFailed() {
        AiKnowledgeSyncService syncService = syncServiceProvider.getIfAvailable();
        if (syncService == null) {
            return ApiResponse.failure("AI_DISABLED", "AI 客服未启用，请先配置百炼与 Qdrant 并打开开关", requestId());
        }
        AiKnowledgeSyncService.SyncResult syncResult = syncService.retryFailed();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", syncResult.total());
        result.put("success", syncResult.success());
        result.put("skipped", syncResult.skipped());
        result.put("failed", syncResult.failed());
        return ApiResponse.success(result, requestId());
    }

    /**
     * 召回测试。
     *
     * 走一遍真实的召回与精排，把中间结果原样返回：命中与否、最高相似度、阈值、召回了哪些条目、
     * 命中的资料长什么样。运营判断「改了之后有没有变好」靠的就是这一组数字，只看对话结果
     * 分不清是知识没灌进去还是模型没答好。
     *
     * 这里不调用大模型，只有一次向量检索加一次精排，所以界面上可以反复点。
     *
     * @param request 测试问句与可选的类目过滤
     * @return 召回测试结果
     * @author Henfon
     * @date 2026-09-21
     */
    @PostMapping("/recall-test")
    @PreAuthorize("hasAuthority('ai:knowledge:sync')")
    public ApiResponse<Map<String, Object>> recallTest(@Valid @RequestBody AiRecallTestRequest request) {
        AiKnowledgeRetriever retriever = retrieverProvider.getIfAvailable();
        if (retriever == null) {
            return ApiResponse.failure("AI_DISABLED", "AI 客服未启用，请先配置百炼与 Qdrant 并打开开关", requestId());
        }
        AiKnowledgeRetriever.RecallTest test = retriever.recallTest(request.getQuestion(), request.getCategoryCode());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hit", test.hit());
        result.put("bestScore", test.bestScore());
        result.put("threshold", test.threshold());
        result.put("chunks", test.chunks());
        result.put("context", test.context());
        return ApiResponse.success(result, requestId());
    }

    /**
     * 获取请求链路标识。
     *
     * @return 请求链路标识
     * @author Henfon
     * @date 2026-09-21
     */
    private String requestId() {
        return MDC.get("requestId");
    }
}
