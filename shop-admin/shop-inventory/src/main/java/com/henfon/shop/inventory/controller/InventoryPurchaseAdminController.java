package com.henfon.shop.inventory.controller;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.inventory.dto.InventoryPurchaseCreateRequest;
import com.henfon.shop.inventory.entity.InventoryPurchaseOrder;
import com.henfon.shop.inventory.service.InventoryPurchaseService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
/** 采购单后台接口。 @author Henfon @date 2026-09-04 */
@RestController
@RequestMapping("/api/admin/inventory/purchases")
public class InventoryPurchaseAdminController {
    private final InventoryPurchaseService service;
    /** 创建控制器。 @author Henfon @date 2026-09-04 */
    public InventoryPurchaseAdminController(InventoryPurchaseService service) { this.service = service; }
    /** 分页查询采购单。 @author Henfon @date 2026-09-04 */
    @GetMapping @PreAuthorize("hasAuthority('inventory:purchase:query')")
    public ApiResponse<IPage<InventoryPurchaseOrder>> page(@RequestParam(required=false) Integer status,@RequestParam(defaultValue="1") long current,@RequestParam(defaultValue="20") long size){ return ApiResponse.success(service.page(status,current,size), MDC.get("requestId")); }
    /** 创建采购单。 @author Henfon @date 2026-09-04 */
    @PostMapping @PreAuthorize("hasAuthority('inventory:purchase:create')")
    public ApiResponse<InventoryPurchaseOrder> create(@Valid @RequestBody InventoryPurchaseCreateRequest request){ return ApiResponse.success(service.create(request), MDC.get("requestId")); }
    /** 验收采购单。 @author Henfon @date 2026-09-04 */
    @PostMapping("/{id}/receive") @PreAuthorize("hasAuthority('inventory:purchase:receive')")
    public ApiResponse<InventoryPurchaseOrder> receive(@PathVariable Long id){ return ApiResponse.success(service.receive(id), MDC.get("requestId")); }
}
