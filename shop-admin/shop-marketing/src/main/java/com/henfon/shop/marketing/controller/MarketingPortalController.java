package com.henfon.shop.marketing.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.marketing.entity.MarketingCoupon;
import com.henfon.shop.marketing.entity.MarketingMemberCoupon;
import com.henfon.shop.marketing.service.MarketingPortalService;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门户优惠券接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@RestController
@RequestMapping("/api/portal/marketing")
public class MarketingPortalController {
    private final MarketingPortalService service;

    /**
     * 创建门户营销控制器。
     *
     * @param service 门户营销服务
     * @author Henfon
     * @date 2026-08-29
     */
    public MarketingPortalController(MarketingPortalService service) {
        this.service = service;
    }

    /**
     * 查询可领取优惠券。
     *
     * @return 优惠券列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/coupons")
    public ApiResponse<List<MarketingCoupon>> coupons() {
        return ApiResponse.success(service.coupons(), MDC.get("requestId"));
    }

    /**
     * 查询会员优惠券。
     *
     * @param memberId 会员ID
     * @param status 领取状态
     * @return 会员优惠券列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/member-coupons")
    public ApiResponse<List<MarketingMemberCoupon>> memberCoupons(@RequestParam Long memberId,
                                                                   @RequestParam(required = false) Integer status) {
        return ApiResponse.success(service.memberCoupons(memberId, status), MDC.get("requestId"));
    }
}
