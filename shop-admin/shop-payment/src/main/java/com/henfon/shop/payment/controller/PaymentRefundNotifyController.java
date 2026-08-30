package com.henfon.shop.payment.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.payment.dto.PaymentRefundNotifyRequest;
import com.henfon.shop.payment.dto.PaymentRefundResponse;
import com.henfon.shop.payment.service.PaymentRefundService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付平台退款异步通知接口。
 *
 * <p>当前仅落库退款结果和实现幂等，真实微信 V3 验签待渠道适配器接入。</p>
 *
 * @author Henfon
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/wx/pay/refund")
public class PaymentRefundNotifyController {

    private final PaymentRefundService refundService;

    /**
     * 创建退款通知控制器。
     *
     * @param refundService 退款应用服务
     * @author Henfon
     * @date 2026-08-30
     */
    public PaymentRefundNotifyController(PaymentRefundService refundService) {
        this.refundService = refundService;
    }

    /**
     * 接收退款结果通知。
     *
     * @param request 退款通知
     * @return 退款单处理结果
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping("/notify")
    public ApiResponse<PaymentRefundResponse> notify(@Valid @RequestBody PaymentRefundNotifyRequest request) {
        return ApiResponse.success(refundService.notifyRefund(request), MDC.get("requestId"));
    }
}
