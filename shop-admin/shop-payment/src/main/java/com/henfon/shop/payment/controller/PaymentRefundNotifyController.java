package com.henfon.shop.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.payment.dto.PaymentRefundNotifyRequest;
import com.henfon.shop.payment.dto.PaymentRefundResponse;
import com.henfon.shop.payment.service.PaymentRefundService;
import com.henfon.shop.payment.wechat.WechatPayException;
import com.henfon.shop.payment.wechat.WechatPayV3CallbackService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.math.BigDecimal;

/**
 * 支付平台退款异步通知接口。
 *
 * <p>保留原有 DTO 联调入口，并通过 /notify/v3 提供微信 V3 回调验签、资源解密和 DTO 适配。</p>
 *
 * @author Henfon
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/wx/pay/refund")
public class PaymentRefundNotifyController {

    private final PaymentRefundService refundService;
    private final WechatPayV3CallbackService callbackService;
    private final ObjectMapper objectMapper;

    /**
     * 创建退款通知控制器。
     *
     * @param refundService 退款应用服务
     * @author Henfon
     * @date 2026-08-30
     */
    public PaymentRefundNotifyController(PaymentRefundService refundService) {
        this.refundService = refundService;
        this.callbackService = WechatPayV3CallbackService.disabled("微信支付 V3 退款回调服务未注入");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 创建支持微信 V3 回调的退款通知控制器。
     *
     * @param refundService 退款应用服务
     * @param callbackProvider 微信 V3 回调安全服务提供器
     * @param objectMapper JSON 解析器
     * @author Henfon
     * @date 2026-08-31
     */
    @Autowired
    public PaymentRefundNotifyController(PaymentRefundService refundService,
                                         ObjectProvider<WechatPayV3CallbackService> callbackProvider,
                                         ObjectMapper objectMapper) {
        this.refundService = refundService;
        this.callbackService = callbackProvider.getIfAvailable(
                () -> WechatPayV3CallbackService.disabled("微信支付 V3 退款回调服务未配置"));
        this.objectMapper = objectMapper;
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

    /**
     * 接收并处理微信支付 V3 退款通知。
     *
     * @param timestamp 微信回调时间戳
     * @param nonce 微信回调随机串
     * @param signature 微信回调签名
     * @param body 微信回调 envelope 原文
     * @return 退款单处理结果
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
        PaymentRefundNotifyRequest request = toRefundNotifyRequest(plaintext, body);
        refundService.notifyRefund(request);
        // 微信 V3 成功确认必须使用平台约定的响应结构，不能返回商城统一响应包装。
        return Map.of("code", "SUCCESS", "message", "");
    }

    /**
     * 将微信退款通知明文转换为现有退款通知 DTO。
     *
     * @param plaintext 解密后的退款 JSON
     * @param rawPayload 回调 envelope 原文
     * @return 兼容退款服务的通知 DTO
     * @author Henfon
     * @date 2026-08-31
     */
    private PaymentRefundNotifyRequest toRefundNotifyRequest(String plaintext, String rawPayload) {
        try {
            JsonNode payload = objectMapper.readTree(plaintext);
            String status = text(payload, "refund_status");
            boolean success = "SUCCESS".equalsIgnoreCase(status);
            if (!success && !"ABNORMAL".equalsIgnoreCase(status) && !"CLOSED".equalsIgnoreCase(status)) {
                throw new WechatPayException("微信退款通知状态未最终确认: " + status);
            }
            String refundNo = text(payload, "out_refund_no");
            String transactionNo = text(payload, "refund_id");
            if (refundNo == null || refundNo.isBlank() || transactionNo == null || transactionNo.isBlank()) {
                throw new WechatPayException("微信退款回调缺少商户退款单号或退款交易号");
            }
            BigDecimal amount = parseRefundAmount(payload);
            return new PaymentRefundNotifyRequest(refundNo, transactionNo, success, rawPayload, amount);
        } catch (WechatPayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new WechatPayException("微信退款通知解析失败", exception);
        }
    }

    /**
     * 解析微信退款通知中的退款金额（分转人民币元）。
     *
     * @param payload 解密后的退款通知 JSON
     * @return 退款金额，缺失时返回空值兼容历史联调报文
     * @author Henfon
     * @date 2026-09-04
     */
    private BigDecimal parseRefundAmount(JsonNode payload) {
        JsonNode amountNode = payload == null ? null : payload.get("amount");
        JsonNode refundNode = amountNode == null ? null : amountNode.get("refund");
        if (refundNode == null || !refundNode.canConvertToLong() || refundNode.asLong() <= 0) {
            return null;
        }
        return BigDecimal.valueOf(refundNode.asLong(), 2);
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
