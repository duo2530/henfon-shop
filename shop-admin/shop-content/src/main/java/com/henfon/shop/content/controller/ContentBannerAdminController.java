package com.henfon.shop.content.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.content.dto.ContentBannerSaveRequest;
import com.henfon.shop.content.entity.ContentBanner;
import com.henfon.shop.content.service.ContentBannerAdminService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 Banner 管理接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/admin/content/banners")
public class ContentBannerAdminController {

    private final ContentBannerAdminService bannerService;

    /**
     * 创建后台 Banner 控制器。
     *
     * @param bannerService Banner 应用服务
     * @author Henfon
     * @date 2026-08-30
     */
    public ContentBannerAdminController(ContentBannerAdminService bannerService) {
        this.bannerService = bannerService;
    }

    /**
     * 分页查询后台 Banner。
     *
     * @param keyword 标题或标签关键字
     * @param status 状态
     * @param current 当前页
     * @param size 页大小
     * @return Banner 分页数据
     * @author Henfon
     * @date 2026-08-30
     */
    @GetMapping
    @PreAuthorize("hasAuthority('content:banner:query')")
    public ApiResponse<IPage<ContentBanner>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(bannerService.page(keyword, status, current, size), requestId());
    }

    /**
     * 保存或更新 Banner。
     *
     * @param request Banner 保存请求
     * @return Banner ID
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping
    @PreAuthorize("hasAuthority('content:banner:query')")
    public ApiResponse<Long> save(@Valid @RequestBody ContentBannerSaveRequest request) {
        return ApiResponse.success(bannerService.save(request), requestId());
    }

    /**
     * 修改 Banner 启停状态。
     *
     * @param id Banner ID
     * @param status 目标状态
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('content:banner:query')")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        bannerService.updateStatus(id, status);
        return ApiResponse.success(requestId());
    }

    /**
     * 删除 Banner。
     *
     * @param id Banner ID
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('content:banner:query')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        bannerService.delete(id);
        return ApiResponse.success(requestId());
    }

    /**
     * 获取请求链路标识。
     *
     * @return 请求链路标识
     * @author Henfon
     * @date 2026-08-30
     */
    private String requestId() {
        return MDC.get("requestId");
    }
}
