package com.henfon.shop.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.inventory.dto.InventorySupplierStockSaveRequest;
import com.henfon.shop.inventory.entity.InventorySupplierStock;
import com.henfon.shop.inventory.service.InventorySupplierStockService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 供应商SKU供货关系后台接口。 @author Henfon @date 2026-09-04 */
@RestController
@RequestMapping("/api/admin/inventory/suppliers/{supplierId}/stocks")
public class InventorySupplierStockAdminController {
    private final InventorySupplierStockService service;
    /** 创建供货关系控制器。 @author Henfon @date 2026-09-04 */
    public InventorySupplierStockAdminController(InventorySupplierStockService service) { this.service = service; }
    /** 分页查询供货关系。 @author Henfon @date 2026-09-04 */
    @GetMapping @PreAuthorize("hasAuthority('inventory:supplier:query')")
    public ApiResponse<IPage<InventorySupplierStock>> page(@PathVariable Long supplierId, @RequestParam(required = false) Long skuId,
                                                           @RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(service.page(supplierId, skuId, current, size), MDC.get("requestId"));
    }
    /** 保存供货关系。 @author Henfon @date 2026-09-04 */
    @PostMapping @PreAuthorize("hasAuthority('inventory:supplier:save')")
    public ApiResponse<Long> save(@PathVariable Long supplierId, @Valid @RequestBody InventorySupplierStockSaveRequest request) {
        return ApiResponse.success(service.save(supplierId, request), MDC.get("requestId"));
    }
    /** 删除供货关系。 @author Henfon @date 2026-09-04 */
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('inventory:supplier:delete')")
    public ApiResponse<Void> delete(@PathVariable Long supplierId, @PathVariable Long id) {
        service.delete(supplierId, id); return ApiResponse.success(MDC.get("requestId"));
    }
}
