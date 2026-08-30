package com.henfon.shop.marketing.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.marketing.dto.MarketingCouponSaveRequest;
import com.henfon.shop.marketing.entity.MarketingCoupon;
import com.henfon.shop.marketing.service.MarketingCouponAdminService;
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
 * 后台优惠券管理接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/admin/marketing/coupons")
public class MarketingCouponAdminController {

    private final MarketingCouponAdminService couponService;

    /**
     * 创建后台优惠券控制器。
     *
     * @param couponService 优惠券管理服务
     * @author Henfon
     * @date 2026-08-30
     */
    public MarketingCouponAdminController(MarketingCouponAdminService couponService) {
        this.couponService = couponService;
    }

    /**
     * 查询后台优惠券分页数据。
     *
     * @param keyword 券码或标题关键字
     * @param status 启停状态
     * @param current 当前页
     * @param size 页大小
     * @return 优惠券分页结果
     * @author Henfon
     * @date 2026-08-30
     */
    @GetMapping
    @PreAuthorize("hasAuthority('marketing:coupon:query')")
    public ApiResponse<IPage<MarketingCoupon>> page(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Integer status,
                                                     @RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(couponService.page(keyword, status, current, size), MDC.get("requestId"));
    }

    /**
     * 保存后台优惠券。
     *
     * @param request 保存请求
     * @return 优惠券ID
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping
    @PreAuthorize("hasAuthority('marketing:coupon:query')")
    public ApiResponse<Long> save(@Valid @RequestBody MarketingCouponSaveRequest request) {
        return ApiResponse.success(couponService.save(request), MDC.get("requestId"));
    }

    /**
     * 修改优惠券启停状态。
     *
     * @param id 优惠券ID
     * @param status 目标状态
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('marketing:coupon:query')")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        couponService.updateStatus(id, status);
        return ApiResponse.success(MDC.get("requestId"));
    }

    /**
     * 删除优惠券。
     *
     * @param id 优惠券ID
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('marketing:coupon:query')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        couponService.delete(id);
        return ApiResponse.success(MDC.get("requestId"));
    }
}
