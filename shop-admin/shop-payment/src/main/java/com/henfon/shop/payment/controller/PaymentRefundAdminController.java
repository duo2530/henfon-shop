package com.henfon.shop.payment.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.payment.dto.PaymentRefundCreateRequest;
import com.henfon.shop.payment.dto.PaymentRefundResponse;
import com.henfon.shop.payment.service.PaymentRefundService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台退款单接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/admin/payment/refunds")
public class PaymentRefundAdminController {

    private final PaymentRefundService refundService;

    /**
     * 创建后台退款控制器。
     *
     * @param refundService 退款应用服务
     * @author Henfon
     * @date 2026-08-30
     */
    public PaymentRefundAdminController(PaymentRefundService refundService) {
        this.refundService = refundService;
    }

    /**
     * 创建退款单。
     *
     * @param request 退款请求
     * @return 退款单
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping
    @PreAuthorize("hasAuthority('trade:order:refund')")
    public ApiResponse<PaymentRefundResponse> create(@Valid @RequestBody PaymentRefundCreateRequest request) {
        return ApiResponse.success(refundService.create(request), MDC.get("requestId"));
    }

    /**
     * 查询退款单。
     *
     * @param refundNo 退款单号
     * @return 退款单
     * @author Henfon
     * @date 2026-08-30
     */
    @GetMapping("/{refundNo}")
    @PreAuthorize("hasAuthority('trade:order:refund')")
    public ApiResponse<PaymentRefundResponse> get(@PathVariable String refundNo) {
        return ApiResponse.success(refundService.get(refundNo), MDC.get("requestId"));
    }
}
