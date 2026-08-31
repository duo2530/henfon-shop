package com.henfon.shop.payment.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于 JDK HttpClient 的微信支付 V3 客户端。
 *
 * <p>只有在证书和密钥配置完整时才由配置类创建；网络失败或渠道非 2xx 响应会抛出异常，
 * 不会返回本地伪造的二维码或退款成功状态。</p>
 *
 * @author Henfon
 * @date 2026-08-31
 */
public final class WechatPayV3HttpClient implements WechatPayClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final WechatPayV3Signer signer;
    private final String apiBaseUrl;
    private final Duration readTimeout;

    /**
     * 创建 HTTP 客户端。
     *
     * @param httpClient JDK HTTP 客户端
     * @param objectMapper JSON 序列化器
     * @param signer V3 请求签名器
     * @param apiBaseUrl 微信支付 API 根地址
     * @author Henfon
     * @date 2026-08-31
     */
    public WechatPayV3HttpClient(HttpClient httpClient, ObjectMapper objectMapper,
                                 WechatPayV3Signer signer, String apiBaseUrl) {
        this(httpClient, objectMapper, signer, apiBaseUrl, Duration.ofSeconds(10));
    }

    /**
     * 创建带读取超时的 HTTP 客户端。
     *
     * @param httpClient JDK HTTP 客户端
     * @param objectMapper JSON 序列化器
     * @param signer V3 请求签名器
     * @param apiBaseUrl 微信支付 API 根地址
     * @param readTimeout 单次请求读取超时
     * @author Henfon
     * @date 2026-08-31
     */
    public WechatPayV3HttpClient(HttpClient httpClient, ObjectMapper objectMapper,
                                 WechatPayV3Signer signer, String apiBaseUrl, Duration readTimeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.signer = signer;
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            throw new IllegalArgumentException("微信支付 API 地址不能为空");
        }
        this.apiBaseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        this.readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
    }

    /**
     * 调用微信 Native V3 下单接口。
     *
     * @param request Native 下单请求
     * @return 渠道下单结果
     * @author Henfon
     * @date 2026-08-31
     */
    @Override
    public WechatNativeOrderResponse createNativeOrder(WechatNativeOrderRequest request) {
        Map<String, Object> amount = Map.of("total", toFen(request.amount()), "currency", "CNY");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appid", request.appId());
        payload.put("mchid", request.merchantId());
        payload.put("description", request.description());
        payload.put("out_trade_no", request.outTradeNo());
        payload.put("notify_url", request.notifyUrl());
        payload.put("amount", amount);
        String body = writeJson(payload);
        JsonNode response = post("/v3/pay/transactions/native", body);
        String codeUrl = text(response, "code_url");
        if (codeUrl == null || codeUrl.isBlank()) {
            throw new WechatPayException("微信 Native 下单响应缺少 code_url");
        }
        return new WechatNativeOrderResponse(codeUrl, response.toString());
    }

    /**
     * 调用微信 V3 原路退款接口。
     *
     * @param request 退款请求
     * @return 渠道退款结果
     * @author Henfon
     * @date 2026-08-31
     */
    @Override
    public WechatRefundResponse refund(WechatRefundRequest request) {
        Map<String, Object> amount = Map.of("refund", toFen(request.refundAmount()),
                "total", toFen(request.totalAmount()), "currency", "CNY");
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request.transactionNo() != null && !request.transactionNo().isBlank()) {
            payload.put("transaction_id", request.transactionNo());
        } else {
            payload.put("out_trade_no", request.outTradeNo());
        }
        payload.put("out_refund_no", request.outRefundNo());
        payload.put("reason", request.reason());
        payload.put("notify_url", request.notifyUrl());
        payload.put("amount", amount);
        JsonNode response = post("/v3/refund/domestic/refunds", writeJson(payload));
        return new WechatRefundResponse(text(response, "refund_id"), text(response, "status"), response.toString());
    }

    /**
     * 发送签名 POST 请求并校验响应状态。
     *
     * @param path API 相对路径
     * @param body JSON 请求体
     * @return JSON 响应节点
     * @author Henfon
     * @date 2026-08-31
     */
    private JsonNode post(String path, String body) {
        String authorization = signer.buildAuthorization("POST", path, body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBaseUrl + path))
                .timeout(readTimeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new WechatPayException("微信支付接口响应异常: HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (WechatPayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new WechatPayException("调用微信支付接口失败", exception);
        }
    }

    /**
     * 将人民币金额转换为分，并拒绝超过两位小数的金额。
     *
     * @param amount 人民币金额
     * @return 金额分
     * @author Henfon
     * @date 2026-08-31
     */
    private long toFen(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("金额必须不超过两位小数", exception);
        }
    }

    /**
     * 序列化 JSON 请求体。
     *
     * @param payload 请求对象
     * @return JSON 原文
     * @author Henfon
     * @date 2026-08-31
     */
    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new WechatPayException("微信支付请求序列化失败", exception);
        }
    }

    /**
     * 读取 JSON 文本字段。
     *
     * @param node JSON 节点
     * @param field 字段名
     * @return 文本值或空值
     * @author Henfon
     * @date 2026-08-31
     */
    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
