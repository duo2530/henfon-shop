package com.henfon.shop.catalog.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.catalog.dto.CatalogProductSaveRequest;
import com.henfon.shop.catalog.dto.CatalogProductContentSaveRequest;
import com.henfon.shop.catalog.dto.CatalogCategorySaveRequest;
import com.henfon.shop.catalog.dto.CatalogSkuSaveRequest;
import com.henfon.shop.catalog.dto.CatalogProductAuditRequest;
import com.henfon.shop.catalog.dto.CatalogProductBatchStatusRequest;
import com.henfon.shop.catalog.entity.CatalogCategory;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.entity.CatalogSku;
import com.henfon.shop.catalog.service.CatalogCategoryService;
import com.henfon.shop.catalog.service.CatalogProductService;
import com.henfon.shop.catalog.service.CatalogSkuService;
import com.henfon.shop.catalog.service.CatalogProductContentService;
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
import java.util.Map;

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
    private final CatalogSkuService catalogSkuService;
    private final CatalogProductContentService catalogProductContentService;

    /**
     * 创建商品目录管理控制器。
     *
     * @param catalogProductService 商品服务
     * @param catalogCategoryService 类目服务
     * @param catalogSkuService SKU服务
     * @param catalogProductContentService 商品内容服务
     * @author Henfon
     * @date 2026-08-29
     */
    public CatalogAdminController(CatalogProductService catalogProductService,
                                  CatalogCategoryService catalogCategoryService,
                                  CatalogSkuService catalogSkuService,
                                  CatalogProductContentService catalogProductContentService) {
        this.catalogProductService = catalogProductService;
        this.catalogCategoryService = catalogCategoryService;
        this.catalogSkuService = catalogSkuService;
        this.catalogProductContentService = catalogProductContentService;
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
     * 按ID批量查询商品。
     *
     * <p>用于活动配置等场景回显已选商品的名称、主图与价格。明细表只保存商品主键，
     * 不存在则自动忽略该ID，返回顺序不保证与入参一致。</p>
     *
     * @param ids 商品ID集合
     * @return 商品列表
     * @author Henfon
     * @date 2026-09-16
     */
    @GetMapping("/products/batch")
    @PreAuthorize("hasAuthority('catalog:product:query')")
    public ApiResponse<List<CatalogProduct>> listProductsByIds(@RequestParam List<Long> ids) {
        return ApiResponse.success(catalogProductService.listByIds(ids), requestId());
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
    @PreAuthorize("hasAuthority('catalog:product:save')")
    public ApiResponse<Long> saveProduct(@Valid @RequestBody CatalogProductSaveRequest request) {
        return ApiResponse.success(catalogProductService.save(request), requestId());
    }

    /**
     * 修改商品上下架状态。
     *
     * @param id 商品ID
     * @param status 目标状态：0草稿、1上架、2下架
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @org.springframework.web.bind.annotation.PutMapping("/products/{id}/status")
    @PreAuthorize("hasAuthority('catalog:product:status')")
    public ApiResponse<Void> updateProductStatus(@PathVariable Long id, @RequestParam Integer status) {
        catalogProductService.updateStatus(id, status);
        return ApiResponse.success(requestId());
    }

    /**
     * 审核商品并在通过后自动上架。
     *
     * @param id 商品ID
     * @param request 审核请求
     * @return 空响应
     * @author Henfon
     * @date 2026-09-04
     */
    @PostMapping("/products/{id}/audit")
    @PreAuthorize("hasAuthority('catalog:product:audit')")
    public ApiResponse<Void> auditProduct(@PathVariable Long id,
                                          @Valid @RequestBody CatalogProductAuditRequest request) {
        // 审核接口统一记录审核结果与备注，便于后台追踪。
        catalogProductService.audit(id, request.approved(), request.remark());
        return ApiResponse.success(requestId());
    }

    /**
     * 批量修改商品上下架状态。
     *
     * @param request 批量状态请求
     * @return 空响应
     * @author Henfon
     * @date 2026-09-04
     */
    @PostMapping("/products/batch-status")
    @PreAuthorize("hasAuthority('catalog:product:status')")
    public ApiResponse<Void> batchUpdateProductStatus(
            @Valid @RequestBody CatalogProductBatchStatusRequest request) {
        catalogProductService.batchUpdateStatus(request.ids(), request.status());
        return ApiResponse.success(requestId());
    }

    /**
     * 查询商品 SKU。
     *
     * @param productId 商品ID
     * @return SKU列表
     * @author Henfon
     * @date 2026-08-30
     */
    @GetMapping("/products/{productId}/skus")
    @PreAuthorize("hasAuthority('catalog:product:query')")
    public ApiResponse<List<CatalogSku>> listSkus(@PathVariable Long productId) {
        return ApiResponse.success(catalogSkuService.listByProduct(productId), requestId());
    }

    /**
     * 保存商品 SKU。
     *
     * @param productId 商品ID
     * @param request SKU保存请求
     * @return SKU ID
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping("/products/{productId}/skus")
    @PreAuthorize("hasAuthority('catalog:product:save')")
    public ApiResponse<Long> saveSku(@PathVariable Long productId,
                                     @Valid @RequestBody CatalogSkuSaveRequest request) {
        return ApiResponse.success(catalogSkuService.save(productId, request), requestId());
    }

    /**
     * 修改 SKU 启停状态。
     *
     * @param productId 商品ID
     * @param skuId SKU ID
     * @param status 目标状态
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @org.springframework.web.bind.annotation.PutMapping("/products/{productId}/skus/{skuId}/status")
    @PreAuthorize("hasAuthority('catalog:product:status')")
    public ApiResponse<Void> updateSkuStatus(@PathVariable Long productId,
                                             @PathVariable Long skuId,
                                             @RequestParam Integer status) {
        catalogSkuService.updateStatus(productId, skuId, status);
        return ApiResponse.success(requestId());
    }

    /**
     * 逻辑删除商品 SKU。
     *
     * @param productId 商品ID
     * @param skuId SKU ID
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @DeleteMapping("/products/{productId}/skus/{skuId}")
    @PreAuthorize("hasAuthority('catalog:product:delete')")
    public ApiResponse<Void> deleteSku(@PathVariable Long productId, @PathVariable Long skuId) {
        catalogSkuService.delete(productId, skuId);
        return ApiResponse.success(requestId());
    }

    /**
     * 查询商品卖点、参数和媒体内容。
     *
     * @param productId 商品ID
     * @return 商品内容聚合数据
     * @author Henfon
     * @date 2026-08-31
     */
    @GetMapping("/products/{productId}/content")
    @PreAuthorize("hasAuthority('catalog:product:query')")
    public ApiResponse<Map<String, Object>> getProductContent(@PathVariable Long productId) {
        return ApiResponse.success(catalogProductContentService.getContent(productId), requestId());
    }

    /**
     * 覆盖保存商品卖点、参数和媒体内容。
     *
     * @param productId 商品ID
     * @param request 内容保存请求
     * @return 保存后的商品内容聚合数据
     * @author Henfon
     * @date 2026-08-31
     */
    @org.springframework.web.bind.annotation.PutMapping("/products/{productId}/content")
    @PreAuthorize("hasAuthority('catalog:product:save')")
    public ApiResponse<Map<String, Object>> saveProductContent(
            @PathVariable Long productId,
            @Valid @RequestBody CatalogProductContentSaveRequest request) {
        return ApiResponse.success(catalogProductContentService.replaceContent(productId, request), requestId());
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
    @PreAuthorize("hasAuthority('catalog:product:delete')")
    public ApiResponse<Void> deleteProduct(@PathVariable Long id) {
        catalogProductService.delete(id);
        return ApiResponse.success(requestId());
    }

    /**
     * 复制商品及其 SKU。
     *
     * @param id 原商品ID
     * @return 新商品ID
     * @author Henfon
     * @date 2026-09-04
     */
    @PostMapping("/products/{id}/copy")
    @PreAuthorize("hasAuthority('catalog:product:save')")
    public ApiResponse<Long> copyProduct(@PathVariable Long id) {
        // 复制结果以草稿形式返回，前端可继续编辑后再上架。
        return ApiResponse.success(catalogProductService.copy(id), requestId());
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
     * 查询后台全部类目。
     *
     * @return 类目列表
     * @author Henfon
     * @date 2026-08-30
     */
    @GetMapping("/categories/manage")
    @PreAuthorize("hasAuthority('catalog:category:query')")
    public ApiResponse<List<CatalogCategory>> listManageCategories() {
        return ApiResponse.success(catalogCategoryService.listAdmin(), requestId());
    }

    /**
     * 保存后台类目。
     *
     * @param request 类目保存请求
     * @return 类目ID
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('catalog:category:query')")
    public ApiResponse<Long> saveCategory(@Valid @RequestBody CatalogCategorySaveRequest request) {
        return ApiResponse.success(catalogCategoryService.save(request), requestId());
    }

    /**
     * 修改类目启停状态。
     *
     * @param id 类目ID
     * @param status 目标状态
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @org.springframework.web.bind.annotation.PutMapping("/categories/{id}/status")
    @PreAuthorize("hasAuthority('catalog:category:query')")
    public ApiResponse<Void> updateCategoryStatus(@PathVariable Long id, @RequestParam Integer status) {
        catalogCategoryService.updateStatus(id, status);
        return ApiResponse.success(requestId());
    }

    /**
     * 删除后台类目。
     *
     * @param id 类目ID
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('catalog:category:query')")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        catalogCategoryService.delete(id);
        return ApiResponse.success(requestId());
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
