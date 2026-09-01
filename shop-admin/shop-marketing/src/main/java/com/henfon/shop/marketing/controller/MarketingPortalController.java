package com.henfon.shop.marketing.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.marketing.entity.MarketingCoupon;
import com.henfon.shop.marketing.entity.MarketingMemberCoupon;
import com.henfon.shop.marketing.dto.MarketingFlashSalePortalResponse;
import com.henfon.shop.marketing.service.MarketingFlashSalePortalService;
import com.henfon.shop.marketing.service.MarketingPortalService;
import com.henfon.shop.marketing.dto.MarketingCouponRedeemRequest;
import com.henfon.shop.marketing.dto.MarketingCouponRollbackRequest;
import com.henfon.shop.identity.security.MemberPrincipalResolver;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;

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
    private final MarketingFlashSalePortalService flashSalePortalService;

    /**
     * 创建门户营销控制器。
     *
     * @param service 门户营销服务
     * @author Henfon
     * @date 2026-08-29
     */
    public MarketingPortalController(MarketingPortalService service,
                                     MarketingFlashSalePortalService flashSalePortalService) {
        this.service = service;
        this.flashSalePortalService = flashSalePortalService;
    }

    /**
     * 查询可领取优惠券。
     *
     * @return 优惠券列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping({"/coupons", "/coupons/"})
    public ApiResponse<List<MarketingCoupon>> coupons() {
        return ApiResponse.success(service.coupons(), MDC.get("requestId"));
    }

    /**
     * 查询当前可购买的秒杀活动及商品库存。
     *
     * @return 进行中的秒杀活动列表
     * @author Henfon
     * @date 2026-09-01
     */
    @GetMapping({"/flash-sales", "/flash-sales/"})
    public ApiResponse<List<MarketingFlashSalePortalResponse>> flashSales() {
        return ApiResponse.success(flashSalePortalService.activeFlashSales(), MDC.get("requestId"));
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
    public ApiResponse<List<MarketingMemberCoupon>> memberCoupons(@RequestParam(required = false) Long memberId,
                                                                   @RequestParam(required = false) Integer status,
                                                                   Authentication authentication) {
        Long currentMemberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        return ApiResponse.success(service.memberCoupons(currentMemberId, status), MDC.get("requestId"));
    }

    /**
     * 领取优惠券。
     *
     * @param couponId 优惠券ID
     * @param memberId 请求会员ID，可为空
     * @param authentication 当前认证信息
     * @return 会员优惠券记录
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping("/coupons/{couponId}/claim")
    public ApiResponse<MarketingMemberCoupon> claim(@PathVariable Long couponId,
                                                     @RequestParam(required = false) Long memberId,
                                                     Authentication authentication) {
        Long currentMemberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        return ApiResponse.success(service.claim(currentMemberId, couponId), MDC.get("requestId"));
    }

    /**
     * 核销优惠券。
     *
     * @param request 核销请求
     * @param memberId 请求会员ID，可为空
     * @param authentication 当前认证信息
     * @return 会员优惠券记录
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping("/coupons/redeem")
    public ApiResponse<MarketingMemberCoupon> redeem(@Valid @RequestBody MarketingCouponRedeemRequest request,
                                                      @RequestParam(required = false) Long memberId,
                                                      Authentication authentication) {
        Long currentMemberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        return ApiResponse.success(service.redeem(currentMemberId, request), MDC.get("requestId"));
    }

    /**
     * 回滚订单优惠券核销。
     *
     * @param request 回滚请求
     * @param memberId 请求会员ID，可为空
     * @param authentication 当前认证信息
     * @return 回滚后的会员优惠券记录
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping("/coupons/rollback")
    public ApiResponse<MarketingMemberCoupon> rollback(@Valid @RequestBody MarketingCouponRollbackRequest request,
                                                        @RequestParam(required = false) Long memberId,
                                                        Authentication authentication) {
        Long currentMemberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        return ApiResponse.success(service.rollback(currentMemberId, request.orderId()), MDC.get("requestId"));
    }
}
