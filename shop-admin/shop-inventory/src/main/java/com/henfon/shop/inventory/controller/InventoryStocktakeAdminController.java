package com.henfon.shop.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.inventory.dto.InventoryStocktakeCompleteRequest;
import com.henfon.shop.inventory.dto.InventoryStocktakeCreateRequest;
import com.henfon.shop.inventory.dto.InventoryStocktakeImportRequest;
import com.henfon.shop.inventory.entity.InventoryStocktake;
import com.henfon.shop.inventory.entity.InventoryStocktakeItem;
import com.henfon.shop.inventory.service.InventoryStocktakeService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台库存盘点管理接口。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@RestController
@RequestMapping("/api/admin/inventory/stocktakes")
public class InventoryStocktakeAdminController {

    private final InventoryStocktakeService stocktakeService;

    /**
     * 创建库存盘点管理控制器。
     *
     * @param stocktakeService 库存盘点应用服务
     * @author Henfon
     * @date 2026-08-31
     */
    public InventoryStocktakeAdminController(InventoryStocktakeService stocktakeService) {
        this.stocktakeService = stocktakeService;
    }

    /**
     * 分页查询库存盘点单。
     *
     * @param warehouseId 仓库ID，可选
     * @param status 盘点状态，可选，0进行中、1已完成、2已取消
     * @param keyword 盘点单号关键字
     * @param current 当前页
     * @param size 页大小
     * @return 盘点单分页数据
     * @author Henfon
     * @date 2026-08-31
     */
    @GetMapping
    @PreAuthorize("hasAuthority('inventory:stocktake:query')")
    public ApiResponse<IPage<InventoryStocktake>> page(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        // 服务层统一限制分页上限，控制器只负责参数绑定和权限校验。
        return ApiResponse.success(stocktakeService.page(warehouseId, status, keyword, current, size),
                MDC.get("requestId"));
    }

    /**
     * 查询盘点单及其库存明细。
     *
     * @param id 盘点单ID
     * @return 盘点明细列表
     * @author Henfon
     * @date 2026-08-31
     */
    @GetMapping("/{id}/items")
    @PreAuthorize("hasAuthority('inventory:stocktake:query')")
    public ApiResponse<List<InventoryStocktakeItem>> items(@PathVariable Long id) {
        return ApiResponse.success(stocktakeService.listItems(id), MDC.get("requestId"));
    }

    /**
     * 批量导入盘点实盘数量。
     *
     * @param id 盘点单ID
     * @param request 批量导入请求
     * @return 导入后的盘点明细
     * @author Henfon
     * @date 2026-08-31
     */
    @org.springframework.web.bind.annotation.PutMapping("/{id}/items/import")
    @PreAuthorize("hasAuthority('inventory:stocktake:import')")
    public ApiResponse<List<InventoryStocktakeItem>> importItems(
            @PathVariable Long id,
            @Valid @RequestBody InventoryStocktakeImportRequest request) {
        return ApiResponse.success(stocktakeService.importCounts(id, request), MDC.get("requestId"));
    }

    /**
     * 创建盘点单并快照仓库库存台账。
     *
     * @param request 创建盘点请求
     * @return 创建后的盘点单
     * @author Henfon
     * @date 2026-08-31
     */
    @PostMapping
    @PreAuthorize("hasAuthority('inventory:stocktake:create')")
    public ApiResponse<InventoryStocktake> create(@Valid @RequestBody InventoryStocktakeCreateRequest request) {
        return ApiResponse.success(stocktakeService.create(request), MDC.get("requestId"));
    }

    /**
     * 提交实盘数量并完成盘点差异调整。
     *
     * @param id 盘点单ID
     * @param request 完成盘点请求
     * @return 完成后的盘点单
     * @author Henfon
     * @date 2026-08-31
     */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('inventory:stocktake:complete')")
    public ApiResponse<InventoryStocktake> complete(@PathVariable Long id,
                                                    @Valid @RequestBody InventoryStocktakeCompleteRequest request) {
        return ApiResponse.success(stocktakeService.complete(id, request), MDC.get("requestId"));
    }
}
