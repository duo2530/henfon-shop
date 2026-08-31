package com.henfon.shop.payment.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.security.MemberPrincipalResolver;
import com.henfon.shop.payment.dto.PaymentInvoiceCreateRequest;
import com.henfon.shop.payment.dto.PaymentInvoiceResponse;
import com.henfon.shop.payment.service.PaymentInvoiceService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门户会员发票申请接口。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@RestController
@RequestMapping("/api/portal/payment/invoices")
public class PaymentInvoicePortalController {

    private final PaymentInvoiceService invoiceService;

    /**
     * 创建门户发票控制器。
     *
     * @param invoiceService 发票应用服务
     * @author Henfon
     * @date 2026-08-31
     */
    public PaymentInvoicePortalController(PaymentInvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * 申请订单发票。
     *
     * @param request 发票申请请求
     * @param authentication 当前认证信息
     * @return 发票申请
     * @author Henfon
     * @date 2026-08-31
     */
    @PostMapping("/orders/{orderId}")
    public ApiResponse<PaymentInvoiceResponse> apply(@PathVariable Long orderId,
                                                     @Valid @RequestBody PaymentInvoiceCreateRequest request,
                                                     Authentication authentication) {
        Long memberId = MemberPrincipalResolver.requireMemberId(authentication, null);
        return ApiResponse.success(invoiceService.create(memberId, orderId, request), MDC.get("requestId"));
    }

    /**
     * 查询当前会员发票申请。
     *
     * @param orderId 订单ID
     * @param authentication 当前认证信息
     * @return 发票申请，不存在时返回空
     * @author Henfon
     * @date 2026-08-31
     */
    @GetMapping("/orders/{orderId}")
    public ApiResponse<PaymentInvoiceResponse> get(@PathVariable Long orderId,
                                                   Authentication authentication) {
        Long memberId = MemberPrincipalResolver.requireMemberId(authentication, null);
        return ApiResponse.success(invoiceService.getMember(memberId, orderId), MDC.get("requestId"));
    }
}
