package com.henfon.shop.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.ai.dto.AiFaqSaveRequest;
import com.henfon.shop.ai.entity.AiFaq;
import com.henfon.shop.ai.service.AiFaqAdminService;
import com.henfon.shop.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台知识库问答维护接口。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@RestController
@RequestMapping("/api/admin/ai/faqs")
public class AiFaqAdminController {

    private final AiFaqAdminService faqService;

    /**
     * 创建知识库问答控制器。
     *
     * @param faqService 知识库问答服务
     * @author Henfon
     * @date 2026-09-21
     */
    public AiFaqAdminController(AiFaqAdminService faqService) {
        this.faqService = faqService;
    }

    /**
     * 分页查询知识库问答。
     *
     * @param keyword 问法或答案关键字
     * @param category 业务分类
     * @param enabled 启用状态
     * @param current 当前页
     * @param size 页大小
     * @return 分页数据
     * @author Henfon
     * @date 2026-09-21
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ai:faq:query')")
    public ApiResponse<IPage<AiFaq>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(faqService.page(keyword, category, enabled, current, size), requestId());
    }

    /**
     * 导出知识库问答为 CSV。
     *
     * 返回 CSV 文本而不是文件：由前端落地成文件并触发下载，后端就不必为一个几百条的内容维护
     * 临时文件与回收逻辑。
     *
     * @param keyword 问法或答案关键字
     * @param category 业务分类
     * @param enabled 启用状态
     * @return CSV 文本
     * @author Henfon
     * @date 2026-09-22
     */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('ai:faq:query')")
    public ApiResponse<String> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer enabled) {
        return ApiResponse.success(faqService.exportCsv(keyword, category, enabled), requestId());
    }

    /**
     * 新增或修改知识库问答。
     *
     * @param request 保存请求
     * @return 记录 ID
     * @author Henfon
     * @date 2026-09-21
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ai:faq:save')")
    public ApiResponse<Long> save(@Valid @RequestBody AiFaqSaveRequest request) {
        return ApiResponse.success(faqService.save(request), requestId());
    }

    /**
     * 删除知识库问答。
     *
     * @param id 记录 ID
     * @return 空响应
     * @author Henfon
     * @date 2026-09-21
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:faq:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        faqService.delete(id);
        return ApiResponse.success(requestId());
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
