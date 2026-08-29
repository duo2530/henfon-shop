package com.henfon.shop.catalog.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.catalog.dto.CatalogProductSaveRequest;
import com.henfon.shop.catalog.entity.CatalogCategory;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.service.CatalogCategoryService;
import com.henfon.shop.catalog.service.CatalogProductService;
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

import java.util.List;

/**
 * 商品目录管理接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@RestController
@RequestMapping("/api/admin/catalog")
public class CatalogAdminController {

    private final CatalogProductService catalogProductService;
    private final CatalogCategoryService catalogCategoryService;

    /**
     * 创建商品目录管理控制器。
     *
     * @param catalogProductService 商品服务
     * @param catalogCategoryService 类目服务
     * @author Henfon
     * @date 2026-08-29
     */
    public CatalogAdminController(CatalogProductService catalogProductService,
                                  CatalogCategoryService catalogCategoryService) {
        this.catalogProductService = catalogProductService;
        this.catalogCategoryService = catalogCategoryService;
    }

    /**
     * 分页查询商品。
     *
     * @param keyword 商品名称、编码或SKU关键字
     * @param categoryId 类目ID
     * @param status 商品状态
     * @param current 当前页
     * @param size 页大小
     * @return 商品分页数据
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/products")
    @PreAuthorize("hasAuthority('catalog:product:query')")
    public ApiResponse<IPage<CatalogProduct>> pageProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(catalogProductService.page(keyword, categoryId, status, current, size), requestId());
    }

    /**
     * 保存商品。
     *
     * @param request 商品保存请求
     * @return 商品ID
     * @author Henfon
     * @date 2026-08-29
     */
    @PostMapping("/products")
    @PreAuthorize("hasAuthority('catalog:product:query')")
    public ApiResponse<Long> saveProduct(@Valid @RequestBody CatalogProductSaveRequest request) {
        return ApiResponse.success(catalogProductService.save(request), requestId());
    }

    /**
     * 删除商品。
     *
     * @param id 商品ID
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAuthority('catalog:product:query')")
    public ApiResponse<Void> deleteProduct(@PathVariable Long id) {
        // 当前阶段沿用商品查询权限，后续补充独立的商品编辑和删除按钮权限。
        catalogProductService.delete(id);
        return ApiResponse.success(requestId());
    }

    /**
     * 查询启用类目。
     *
     * @return 类目列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('catalog:product:query')")
    public ApiResponse<List<CatalogCategory>> listCategories() {
        return ApiResponse.success(catalogCategoryService.listEnabled(), requestId());
    }

    /**
     * 获取请求链路标识。
     *
     * @return 请求链路标识
     * @author Henfon
     * @date 2026-08-29
     */
    private String requestId() {
        return MDC.get("requestId");
    }
}
