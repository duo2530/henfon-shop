package com.henfon.shop.payment.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.security.MemberPrincipalResolver;
import com.henfon.shop.payment.dto.PaymentCreateRequest;
import com.henfon.shop.payment.dto.PaymentOrderResponse;
import com.henfon.shop.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门户支付单接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/portal/payment")
public class PaymentPortalController {

    private final PaymentService paymentService;

    /**
     * 创建门户支付控制器。
     *
     * @param paymentService 支付应用服务
     * @author Henfon
     * @date 2026-08-30
     */
    public PaymentPortalController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 创建订单支付单。
     *
     * @param orderId 订单ID
     * @param memberId 请求中的会员ID
     * @param request 支付渠道
     * @param authentication 当前认证信息
     * @return 支付单
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping("/orders/{orderId}")
    public ApiResponse<PaymentOrderResponse> create(@PathVariable Long orderId,
                                                    @RequestParam(required = false) Long memberId,
                                                    @Valid @RequestBody PaymentCreateRequest request,
                                                    Authentication authentication) {
        Long currentMemberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        return ApiResponse.success(paymentService.create(currentMemberId, orderId, request), MDC.get("requestId"));
    }

    /**
     * 查询订单支付单。
     *
     * @param paymentNo 支付单号
     * @param memberId 请求中的会员ID
     * @param authentication 当前认证信息
     * @return 支付单
     * @author Henfon
     * @date 2026-08-30
     */
    @GetMapping("/orders/{paymentNo}")
    public ApiResponse<PaymentOrderResponse> get(@PathVariable String paymentNo,
                                                 @RequestParam(required = false) Long memberId,
                                                 Authentication authentication) {
        Long currentMemberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        return ApiResponse.success(paymentService.get(currentMemberId, paymentNo), MDC.get("requestId"));
    }

    /**
     * 关闭订单支付单。
     *
     * @param paymentNo 支付单号
     * @param memberId 请求中的会员ID
     * @param authentication 当前认证信息
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @PutMapping("/orders/{paymentNo}/close")
    public ApiResponse<Void> close(@PathVariable String paymentNo,
                                   @RequestParam(required = false) Long memberId,
                                   Authentication authentication) {
        Long currentMemberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        paymentService.close(currentMemberId, paymentNo);
        return ApiResponse.success(MDC.get("requestId"));
    }
}
