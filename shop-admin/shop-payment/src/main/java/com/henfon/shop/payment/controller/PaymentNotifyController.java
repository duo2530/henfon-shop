package com.henfon.shop.payment.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.payment.dto.PaymentNotifyRequest;
import com.henfon.shop.payment.dto.PaymentOrderResponse;
import com.henfon.shop.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付平台异步通知接口。
 *
 * <p>当前仅处理支付单幂等和订单状态联动，微信 V3 签名验签由后续渠道适配器补齐。</p>
 *
 * @author Henfon
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/wx/pay")
public class PaymentNotifyController {

    private final PaymentService paymentService;

    /**
     * 创建支付通知控制器。
     *
     * @param paymentService 支付应用服务
     * @author Henfon
     * @date 2026-08-30
     */
    public PaymentNotifyController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 接收支付成功异步通知。
     *
     * @param request 支付通知内容
     * @return 支付单处理结果
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping("/notify")
    public ApiResponse<PaymentOrderResponse> notify(@Valid @RequestBody PaymentNotifyRequest request) {
        return ApiResponse.success(paymentService.notifyPayment(request), MDC.get("requestId"));
    }
}
