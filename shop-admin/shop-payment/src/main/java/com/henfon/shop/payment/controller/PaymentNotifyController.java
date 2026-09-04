package com.henfon.shop.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.payment.dto.PaymentNotifyRequest;
import com.henfon.shop.payment.dto.PaymentOrderResponse;
import com.henfon.shop.payment.service.PaymentService;
import com.henfon.shop.payment.wechat.WechatPayException;
import com.henfon.shop.payment.wechat.WechatPayV3CallbackService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.math.BigDecimal;

/**
 * 支付平台异步通知接口。
 *
 * <p>保留原有 DTO 联调入口，并通过 /notify/v3 提供微信 V3 回调验签、资源解密和 DTO 适配。</p>
 *
 * @author Henfon
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/wx/pay")
public class PaymentNotifyController {

    private final PaymentService paymentService;
    private final WechatPayV3CallbackService callbackService;
    private final ObjectMapper objectMapper;

    /**
     * 创建支付通知控制器。
     *
     * @param paymentService 支付应用服务
     * @author Henfon
     * @date 2026-08-30
     */
    public PaymentNotifyController(PaymentService paymentService) {
        this.paymentService = paymentService;
        this.callbackService = WechatPayV3CallbackService.disabled("微信支付 V3 回调服务未注入");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 创建支持微信 V3 回调的支付通知控制器。
     *
     * @param paymentService 支付应用服务
     * @param callbackProvider 微信 V3 回调安全服务提供器
     * @param objectMapper JSON 解析器
     * @author Henfon
     * @date 2026-08-31
     */
    @Autowired
    public PaymentNotifyController(PaymentService paymentService,
                                   ObjectProvider<WechatPayV3CallbackService> callbackProvider,
                                   ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.callbackService = callbackProvider.getIfAvailable(
                () -> WechatPayV3CallbackService.disabled("微信支付 V3 回调服务未配置"));
        this.objectMapper = objectMapper;
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

    /**
     * 接收并处理微信支付 V3 原始通知。
     *
     * @param timestamp 微信回调时间戳
     * @param nonce 微信回调随机串
     * @param signature 微信回调签名
     * @param body 微信回调 envelope 原文
     * @return 支付单处理结果
     * @author Henfon
     * @date 2026-08-31
     */
    @PostMapping("/notify/v3")
    public Map<String, String> notifyV3(
            @RequestHeader("Wechatpay-Timestamp") String timestamp,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestBody String body) {
        String plaintext = callbackService.verifyAndDecryptNotification(timestamp, nonce, signature, body,
                objectMapper);
        PaymentNotifyRequest request = toPaymentNotifyRequest(plaintext, body);
        paymentService.notifyPayment(request);
        // 微信 V3 成功确认必须使用平台约定的响应结构，不能返回商城统一响应包装。
        return Map.of("code", "SUCCESS", "message", "");
    }

    /**
     * 将微信支付交易通知明文转换为现有支付通知 DTO。
     *
     * @param plaintext 解密后的交易 JSON
     * @param rawPayload 回调 envelope 原文
     * @return 兼容支付服务的通知 DTO
     * @author Henfon
     * @date 2026-08-31
     */
    private PaymentNotifyRequest toPaymentNotifyRequest(String plaintext, String rawPayload) {
        try {
            JsonNode payload = objectMapper.readTree(plaintext);
            String state = text(payload, "trade_state");
            if (!"SUCCESS".equalsIgnoreCase(state)) {
                throw new WechatPayException("微信支付交易状态未成功: " + state);
            }
            String paymentNo = text(payload, "out_trade_no");
            String transactionNo = text(payload, "transaction_id");
            if (paymentNo == null || paymentNo.isBlank() || transactionNo == null || transactionNo.isBlank()) {
                throw new WechatPayException("微信支付回调缺少商户支付单号或交易号");
            }
            JsonNode amountNode = payload.get("amount");
            JsonNode totalNode = amountNode == null ? null : amountNode.get("total");
            if (totalNode == null || !totalNode.canConvertToLong() || totalNode.asLong() <= 0) {
                throw new WechatPayException("微信支付回调缺少合法的支付金额");
            }
            BigDecimal amount = BigDecimal.valueOf(totalNode.asLong(), 2);
            return new PaymentNotifyRequest(paymentNo, transactionNo, rawPayload, amount);
        } catch (WechatPayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new WechatPayException("微信支付交易通知解析失败", exception);
        }
    }

    /**
     * 读取 JSON 文本字段。
     *
     * @param node JSON 节点
     * @param field 字段名称
     * @return 字段文本或空值
     * @author Henfon
     * @date 2026-08-31
     */
    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
