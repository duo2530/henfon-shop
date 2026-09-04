package com.henfon.shop.payment.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.payment.dto.PaymentInvoiceStatusRequest;
import com.henfon.shop.payment.service.PaymentInvoiceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用开票平台回传接口，供外部平台异步通知开票结果。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@RestController
@RequestMapping("/api/payment/invoices")
public class PaymentInvoiceCallbackController {

    private final PaymentInvoiceService invoiceService;
    private final String callbackToken;

    /**
     * 创建开票平台回调控制器。
     *
     * @param invoiceService 发票服务
     * @param callbackToken 回调鉴权令牌
     * @author Henfon
     * @date 2026-09-04
     */
    public PaymentInvoiceCallbackController(PaymentInvoiceService invoiceService,
                                             @Value("${shop.payment.invoice.callback-token:}") String callbackToken) {
        this.invoiceService = invoiceService;
        this.callbackToken = callbackToken;
    }

    /**
     * 接收平台开票状态回传并幂等更新本地发票申请。
     *
     * @param invoiceNo 发票申请号
     * @param request 状态及发票地址/失败原因
     * @param token 回调鉴权令牌
     * @return 更新后的发票记录
     * @author Henfon
     * @date 2026-09-04
     */
    @PostMapping("/callback")
    public ApiResponse<?> callback(@RequestHeader(value = "X-Invoice-Token", required = false) String token,
                                   @RequestBody @Valid InvoiceCallbackRequest request) {
        if (!StringUtils.hasText(callbackToken) || !callbackToken.equals(token)) {
            throw new BusinessException("PAYMENT_INVOICE_CALLBACK_FORBIDDEN", "开票平台回调鉴权失败");
        }
        return ApiResponse.success(invoiceService.updateStatus(request.invoiceNo(), request.statusRequest()), MDC.get("requestId"));
    }

    /**
     * 通用开票平台回调请求体。
     *
     * @param invoiceNo 发票申请号
     * @param status 状态
     * @param invoiceUrl 发票文件地址
     * @param failureReason 失败原因
     * @author Henfon
     * @date 2026-09-04
     */
    public record InvoiceCallbackRequest(@NotBlank String invoiceNo,
                                         @NotNull @Min(0) @Max(4) Integer status,
                                         String invoiceUrl, String failureReason) {
        /**
         * 转换为内部发票状态请求。
         *
         * @return 状态请求
         * @author Henfon
         * @date 2026-09-04
         */
        public PaymentInvoiceStatusRequest statusRequest() {
            return new PaymentInvoiceStatusRequest(status, invoiceUrl, failureReason);
        }
    }
}
