package com.henfon.shop.content.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.content.dto.ContentReviewSubmitRequest;
import com.henfon.shop.content.entity.ContentBanner;
import com.henfon.shop.content.entity.ContentReview;
import com.henfon.shop.content.service.ContentPortalService;
import com.henfon.shop.identity.security.AuthenticatedUser;
import com.henfon.shop.identity.security.MemberPrincipalResolver;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * 分页查询商品评价，仅返回审核通过的内容。
     *
     * @param productId 商品ID
     * @param current 当前页
     * @param size 页大小
     * @return 评价分页数据
     * @author Henfon
     * @date 2026-08-30
     */
    @GetMapping("/products/{productId}/reviews/page")
    public ApiResponse<IPage<ContentReview>> reviewPage(@PathVariable Long productId,
                                                        @RequestParam(defaultValue = "1") long current,
                                                        @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(service.reviewPage(productId, current, size), MDC.get("requestId"));
    }

    /**
     * 提交商品评价，新增记录进入待审核状态。
     *
     * @param productId 商品ID
     * @param request 评价内容
     * @param authentication 当前会员认证信息
     * @return 新评价ID
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping("/products/{productId}/reviews")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> submitReview(@PathVariable Long productId,
                                          @Valid @RequestBody ContentReviewSubmitRequest request,
                                          Authentication authentication) {
        Long memberId = MemberPrincipalResolver.requireMemberId(authentication);
        String memberName = authentication.getPrincipal() instanceof AuthenticatedUser user
                ? user.username() : "会员";
        return ApiResponse.success(service.submitReview(productId, memberId, memberName, request),
                MDC.get("requestId"));
    }
}
