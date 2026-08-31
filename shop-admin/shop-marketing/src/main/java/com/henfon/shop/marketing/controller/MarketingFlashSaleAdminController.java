package com.henfon.shop.marketing.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.marketing.dto.MarketingFlashSaleSaveRequest;
import com.henfon.shop.marketing.entity.MarketingFlashSale;
import com.henfon.shop.marketing.entity.MarketingFlashSaleItem;
import com.henfon.shop.marketing.service.MarketingFlashSaleService;
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

import java.util.List;

/**
 * 后台秒杀促销活动管理接口。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@RestController
@RequestMapping("/api/admin/marketing/flash-sales")
public class MarketingFlashSaleAdminController {

    private final MarketingFlashSaleService flashSaleService;

    /**
     * 创建秒杀活动管理控制器。
     *
     * @param flashSaleService 秒杀活动应用服务
     * @author Henfon
     * @date 2026-08-31
     */
    public MarketingFlashSaleAdminController(MarketingFlashSaleService flashSaleService) {
        this.flashSaleService = flashSaleService;
    }

    /**
     * 分页查询活动。
     *
     * @param keyword 活动编码或名称关键字
     * @param status 活动状态
     * @param current 当前页
     * @param size 页大小
     * @return 活动分页结果
     * @author Henfon
     * @date 2026-08-31
     */
    @GetMapping
    @PreAuthorize("hasAuthority('marketing:flash:query')")
    public ApiResponse<IPage<MarketingFlashSale>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(flashSaleService.page(keyword, status, current, size), MDC.get("requestId"));
    }

    /**
     * 查询活动商品。
     *
     * @param id 活动ID
     * @return 活动商品列表
     * @author Henfon
     * @date 2026-08-31
     */
    @GetMapping("/{id}/items")
    @PreAuthorize("hasAuthority('marketing:flash:query')")
    public ApiResponse<List<MarketingFlashSaleItem>> listItems(@PathVariable Long id) {
        return ApiResponse.success(flashSaleService.listItems(id), MDC.get("requestId"));
    }

    /**
     * 新增或编辑活动。
     *
     * @param request 活动保存请求
     * @return 活动ID
     * @author Henfon
     * @date 2026-08-31
     */
    @PostMapping
    @PreAuthorize("hasAuthority('marketing:flash:save')")
    public ApiResponse<Long> save(@Valid @RequestBody MarketingFlashSaleSaveRequest request) {
        return ApiResponse.success(flashSaleService.save(request), MDC.get("requestId"));
    }

    /**
     * 修改活动状态。
     *
     * @param id 活动ID
     * @param status 目标状态
     * @return 空响应
     * @author Henfon
     * @date 2026-08-31
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('marketing:flash:status')")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        flashSaleService.updateStatus(id, status);
        return ApiResponse.success(MDC.get("requestId"));
    }

    /**
     * 逻辑删除活动。
     *
     * @param id 活动ID
     * @return 空响应
     * @author Henfon
     * @date 2026-08-31
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('marketing:flash:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        flashSaleService.delete(id);
        return ApiResponse.success(MDC.get("requestId"));
    }
}
