package com.henfon.shop.content.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.content.dto.ContentReviewReplyRequest;
import com.henfon.shop.content.entity.ContentReview;
import com.henfon.shop.content.service.ContentReviewAdminService;
import com.henfon.shop.identity.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台商品评价审核接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/admin/content/reviews")
public class ContentReviewAdminController {

    private final ContentReviewAdminService reviewService;

    /**
     * 创建商品评价审核控制器。
     *
     * @param reviewService 评价审核应用服务
     * @author Henfon
     * @date 2026-08-30
     */
    public ContentReviewAdminController(ContentReviewAdminService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * 分页查询商品评价。
     *
     * @param productId 商品ID，可选
     * @param status 展示状态，可选
     * @param keyword 评价或会员关键字
     * @param current 当前页
     * @param size 页大小
     * @return 评价分页数据
     * @author Henfon
     * @date 2026-08-30
     */
    @GetMapping
    @PreAuthorize("hasAuthority('content:review:query')")
    public ApiResponse<IPage<ContentReview>> page(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(reviewService.page(productId, status, keyword, current, size), requestId());
    }

    /**
     * 审核或隐藏评价。
     *
     * @param id 评价ID
     * @param status 目标状态，1展示、0隐藏
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('content:review:query')")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        reviewService.updateStatus(id, status);
        return ApiResponse.success(requestId());
    }

    /**
     * 回复商品评价。
     *
     * @param id 评价ID
     * @param request 回复内容
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @PutMapping("/{id}/reply")
    @PreAuthorize("hasAuthority('content:review:query')")
    public ApiResponse<Void> reply(@PathVariable Long id,
                                   @Valid @RequestBody ContentReviewReplyRequest request,
                                   Authentication authentication) {
        String repliedBy = authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? user.username() : null;
        reviewService.reply(id, request, repliedBy);
        return ApiResponse.success(requestId());
    }

    /**
     * 获取请求链路标识。
     *
     * @return 请求链路标识
     * @author Henfon
     * @date 2026-08-30
     */
    private String requestId() {
        return MDC.get("requestId");
    }
}
