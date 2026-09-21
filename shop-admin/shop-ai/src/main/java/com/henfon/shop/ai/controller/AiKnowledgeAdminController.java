package com.henfon.shop.ai.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.henfon.shop.ai.entity.AiVectorSync;
import com.henfon.shop.ai.mapper.AiVectorSyncMapper;
import com.henfon.shop.ai.service.AiKnowledgeSyncService;
import com.henfon.shop.common.api.ApiResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 知识库向量同步接口。
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

    private final ObjectProvider<AiKnowledgeSyncService> syncServiceProvider;

    private final AiVectorSyncMapper vectorSyncMapper;

    /**
     * 创建知识库同步控制器。
     *
     * @param syncServiceProvider 向量同步服务，AI 未启用时为空
     * @param vectorSyncMapper 向量同步位点 Mapper
     * @author Henfon
     * @date 2026-09-21
     */
    public AiKnowledgeAdminController(ObjectProvider<AiKnowledgeSyncService> syncServiceProvider,
                                      AiVectorSyncMapper vectorSyncMapper) {
        this.syncServiceProvider = syncServiceProvider;
        this.vectorSyncMapper = vectorSyncMapper;
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
     * @return 按状态统计的位点数量
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping("/status")
    @PreAuthorize("hasAuthority('ai:knowledge:sync')")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        long synced = vectorSyncMapper.selectCount(Wrappers.lambdaQuery(AiVectorSync.class)
                .eq(AiVectorSync::getStatus, "SYNCED"));
        long pending = vectorSyncMapper.selectCount(Wrappers.lambdaQuery(AiVectorSync.class)
                .eq(AiVectorSync::getStatus, "PENDING"));
        long failed = vectorSyncMapper.selectCount(Wrappers.lambdaQuery(AiVectorSync.class)
                .eq(AiVectorSync::getStatus, "FAILED"));
        result.put("synced", synced);
        result.put("pending", pending);
        result.put("failed", failed);
        result.put("enabled", syncServiceProvider.getIfAvailable() != null);
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
