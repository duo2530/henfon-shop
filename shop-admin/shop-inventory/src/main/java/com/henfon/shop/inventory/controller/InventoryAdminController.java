package com.henfon.shop.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.inventory.dto.InventoryAdjustRequest;
import com.henfon.shop.inventory.dto.InventoryStockSaveRequest;
import com.henfon.shop.inventory.entity.InventoryStock;
import com.henfon.shop.inventory.service.InventoryStockService;
import com.henfon.shop.inventory.service.InventoryReservationExpiryService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台库存管理接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/admin/inventory")
public class InventoryAdminController {

    private final InventoryStockService inventoryStockService;
    private final InventoryReservationExpiryService expiryService;

    /**
     * 创建库存管理控制器。
     *
     * @param inventoryStockService 库存应用服务
     * @author Henfon
     * @date 2026-08-30
     */
    public InventoryAdminController(InventoryStockService inventoryStockService,
                                    InventoryReservationExpiryService expiryService) {
        this.inventoryStockService = inventoryStockService;
        this.expiryService = expiryService;
    }

    /**
     * 分页查询库存。
     *
     * @param skuId SKU ID
     * @param current 当前页
     * @param size 页大小
     * @return 库存分页数据
     * @author Henfon
     * @date 2026-08-30
     */
    @GetMapping("/stocks")
    @PreAuthorize("hasAuthority('inventory:stock:query')")
    public ApiResponse<IPage<InventoryStock>> page(@RequestParam(required = false) Long skuId,
                                                   @RequestParam(defaultValue = "1") long current,
                                                   @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(inventoryStockService.page(skuId, current, size), MDC.get("requestId"));
    }

    /**
     * 查询安全库存预警。
     *
     * @return 低库存台账列表
     * @author Henfon
     * @date 2026-08-30
     */
    @GetMapping("/stocks/warnings")
    @PreAuthorize("hasAuthority('inventory:stock:query')")
    public ApiResponse<List<InventoryStock>> warnings() {
        return ApiResponse.success(inventoryStockService.listWarnings(), MDC.get("requestId"));
    }

    /**
     * 调整库存。
     *
     * @param stockId 台账ID
     * @param request 调整请求
     * @return 调整后的台账
     * @author Henfon
     * @date 2026-08-30
     */
    @PutMapping("/stocks/{stockId}/adjust")
    @PreAuthorize("hasAuthority('inventory:stock:adjust')")
    public ApiResponse<InventoryStock> adjust(@PathVariable Long stockId,
                                              @Valid @RequestBody InventoryAdjustRequest request) {
        return ApiResponse.success(inventoryStockService.adjust(stockId, request.changeQuantity(), request.remark()),
                MDC.get("requestId"));
    }

    /**
     * 初始化或更新 SKU 库存台账。
     *
     * @param request 台账请求
     * @return 保存后的库存台账
     * @author Henfon
     * @date 2026-08-30
     */
    @org.springframework.web.bind.annotation.PostMapping("/stocks")
    @PreAuthorize("hasAuthority('inventory:stock:adjust')")
    public ApiResponse<InventoryStock> save(@Valid @RequestBody InventoryStockSaveRequest request) {
        return ApiResponse.success(inventoryStockService.save(request), MDC.get("requestId"));
    }

    /**
     * 手工触发库存预占过期补偿。
     *
     * @param limit 最多处理数量
     * @return 实际释放数量
     * @author Henfon
     * @date 2026-08-30
     */
    @org.springframework.web.bind.annotation.PostMapping("/locks/expire-compensate")
    @PreAuthorize("hasAuthority('inventory:stock:adjust')")
    public ApiResponse<Integer> compensateExpiredLocks(@RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.success(expiryService.compensate(limit), MDC.get("requestId"));
    }
}
