package com.henfon.shop.payment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.payment.dto.PaymentInvoiceStatusRequest;
import com.henfon.shop.payment.entity.PaymentInvoice;
import com.henfon.shop.payment.service.PaymentInvoiceService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台发票管理接口。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@RestController
@RequestMapping("/api/admin/payment/invoices")
public class PaymentInvoiceAdminController {

    private final PaymentInvoiceService invoiceService;

    /**
     * 创建后台发票控制器。
     *
     * @param invoiceService 发票应用服务
     * @author Henfon
     * @date 2026-08-31
     */
    public PaymentInvoiceAdminController(PaymentInvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * 分页查询发票申请。
     *
     * @param orderId 订单ID，可选
     * @param memberId 会员ID，可选
     * @param keyword 发票单号、订单号或抬头关键字
     * @param status 发票状态，可选
     * @param current 当前页
     * @param size 页大小
     * @return 发票分页数据
     * @author Henfon
     * @date 2026-08-31
     */
    @GetMapping
    @PreAuthorize("hasAuthority('payment:invoice:query')")
    public ApiResponse<IPage<PaymentInvoice>> page(@RequestParam(required = false) Long orderId,
                                                   @RequestParam(required = false) Long memberId,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Integer status,
                                                   @RequestParam(defaultValue = "1") long current,
                                                   @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(invoiceService.page(orderId, memberId, keyword, status, current, size),
                MDC.get("requestId"));
    }

    /**
     * 更新发票状态和开票信息。
     *
     * @param invoiceNo 发票申请号
     * @param request 状态更新请求
     * @return 更新后的发票
     * @author Henfon
     * @date 2026-08-31
     */
    @PutMapping("/{invoiceNo}/status")
    @PreAuthorize("hasAuthority('payment:invoice:status')")
    public ApiResponse<com.henfon.shop.payment.dto.PaymentInvoiceResponse> updateStatus(
            @PathVariable String invoiceNo, @Valid @RequestBody PaymentInvoiceStatusRequest request) {
        return ApiResponse.success(invoiceService.updateStatus(invoiceNo, request), MDC.get("requestId"));
    }
}
