package com.henfon.shop.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.inventory.dto.InventorySupplierSaveRequest;
import com.henfon.shop.inventory.entity.InventorySupplier;
import com.henfon.shop.inventory.service.InventorySupplierService;
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
 * 后台库存供应商管理接口。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@RestController
@RequestMapping("/api/admin/inventory/suppliers")
public class InventorySupplierAdminController {

    private final InventorySupplierService supplierService;

    /**
     * 创建供应商管理控制器。
     *
     * @param supplierService 供应商应用服务
     * @author Henfon
     * @date 2026-08-31
     */
    public InventorySupplierAdminController(InventorySupplierService supplierService) {
        this.supplierService = supplierService;
    }

    /**
     * 分页查询供应商。
     *
     * @param keyword 供应商编码、名称或联系人关键字
     * @param status 供应商状态
     * @param current 当前页
     * @param size 页大小
     * @return 供应商分页数据
     * @author Henfon
     * @date 2026-08-31
     */
    @GetMapping
    @PreAuthorize("hasAuthority('inventory:supplier:query')")
    public ApiResponse<IPage<InventorySupplier>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        // 分页参数交由服务层统一规整，确保不同库存管理接口行为一致。
        return ApiResponse.success(supplierService.page(keyword, status, current, size), MDC.get("requestId"));
    }

    /**
     * 新增或编辑供应商。
     *
     * @param request 供应商保存请求
     * @return 保存后的供应商ID
     * @author Henfon
     * @date 2026-08-31
     */
    @PostMapping
    @PreAuthorize("hasAuthority('inventory:supplier:save')")
    public ApiResponse<Long> save(@Valid @RequestBody InventorySupplierSaveRequest request) {
        return ApiResponse.success(supplierService.save(request), MDC.get("requestId"));
    }

    /**
     * 修改供应商启停状态。
     *
     * @param id 供应商ID
     * @param status 目标状态，1启用、0停用
     * @return 空响应
     * @author Henfon
     * @date 2026-08-31
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('inventory:supplier:status')")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        supplierService.updateStatus(id, status);
        return ApiResponse.success(MDC.get("requestId"));
    }

    /**
     * 逻辑删除供应商。
     *
     * @param id 供应商ID
     * @return 空响应
     * @author Henfon
     * @date 2026-08-31
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('inventory:supplier:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return ApiResponse.success(MDC.get("requestId"));
    }
}
