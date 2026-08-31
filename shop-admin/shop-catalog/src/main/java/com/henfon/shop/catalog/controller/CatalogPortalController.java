package com.henfon.shop.catalog.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.catalog.entity.CatalogCategory;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.service.CatalogCategoryService;
import com.henfon.shop.catalog.service.CatalogPortalService;
import com.henfon.shop.common.api.ApiResponse;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

/**
 * 门户商品查询接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@RestController
@RequestMapping("/api/portal/catalog")
public class CatalogPortalController {
    private final CatalogPortalService portalService;
    private final CatalogCategoryService categoryService;

    /**
     * 创建门户商品控制器。
     *
     * @param portalService 门户商品服务
     * @param categoryService 类目服务
     * @author Henfon
     * @date 2026-08-29
     */
    public CatalogPortalController(CatalogPortalService portalService, CatalogCategoryService categoryService) {
        this.portalService = portalService;
        this.categoryService = categoryService;
    }

    /**
     * 分页查询上架商品。
     *
     * @param keyword 搜索关键字
     * @param categoryId 类目ID
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @param sortBy 排序方式：featured、newest、sales、price-asc、price-desc
     * @param current 页码
     * @param size 页大小
     * @return 商品分页数据
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/products")
    public ApiResponse<IPage<CatalogProduct>> products(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Long categoryId,
                                                        @RequestParam(required = false) BigDecimal minPrice,
                                                        @RequestParam(required = false) BigDecimal maxPrice,
                                                        @RequestParam(required = false) String sortBy,
                                                        @RequestParam(defaultValue = "1") long current,
                                                        @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(portalService.page(keyword, categoryId, minPrice, maxPrice, sortBy, current, size), MDC.get("requestId"));
    }

    /**
     * 查询商品详情。
     *
     * @param id 商品ID
     * @return 商品详情聚合数据
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/products/{id}")
    public ApiResponse<Map<String, Object>> product(@PathVariable Long id) {
        Map<String, Object> detail = portalService.detail(id);
        if (detail == null) {
            return ApiResponse.failure("CATALOG_PRODUCT_NOT_FOUND", "商品不存在或已下架", MDC.get("requestId"));
        }
        return ApiResponse.success(detail, MDC.get("requestId"));
    }

    /**
     * 查询门户启用类目。
     *
     * @return 类目列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/categories")
    public ApiResponse<List<CatalogCategory>> categories() {
        return ApiResponse.success(categoryService.listEnabled(), MDC.get("requestId"));
    }
}
