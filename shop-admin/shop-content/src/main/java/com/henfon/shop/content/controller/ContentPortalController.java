package com.henfon.shop.content.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.content.entity.ContentBanner;
import com.henfon.shop.content.entity.ContentReview;
import com.henfon.shop.content.service.ContentPortalService;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门户内容接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@RestController
@RequestMapping("/api/portal/content")
public class ContentPortalController {
    private final ContentPortalService service;

    /**
     * 创建门户内容控制器。
     *
     * @param service 门户内容服务
     * @author Henfon
     * @date 2026-08-29
     */
    public ContentPortalController(ContentPortalService service) {
        this.service = service;
    }

    /**
     * 查询首页 Banner。
     *
     * @return Banner 列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/banners")
    public ApiResponse<List<ContentBanner>> banners() {
        return ApiResponse.success(service.banners(), MDC.get("requestId"));
    }

    /**
     * 查询商品评价。
     *
     * @param productId 商品ID
     * @param limit 返回条数
     * @return 评价列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/products/{productId}/reviews")
    public ApiResponse<List<ContentReview>> reviews(@PathVariable Long productId,
                                                    @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(service.reviews(productId, limit), MDC.get("requestId"));
    }
}
