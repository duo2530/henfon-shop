package com.henfon.shop.payment.wechat;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * 微信支付 API V3 请求签名器。
 *
 * <p>签名串严格遵循 method、canonical URL、timestamp、nonce 和 body 五行格式，
 * 使用商户 API 私钥执行 SHA256withRSA。</p>
 *
 * @author Henfon
 * @date 2026-08-31
 */
public final class WechatPayV3Signer {

    private final String merchantId;
    private final String merchantSerialNumber;
    private final PrivateKey privateKey;

    /**
     * 创建签名器。
     *
     * @param merchantId 商户号
     * @param merchantSerialNumber 商户证书序列号
     * @param privateKey 商户 API 私钥
     * @author Henfon
     * @date 2026-08-31
     */
    public WechatPayV3Signer(String merchantId, String merchantSerialNumber, PrivateKey privateKey) {
        this.merchantId = requireText(merchantId, "商户号");
        this.merchantSerialNumber = requireText(merchantSerialNumber, "商户证书序列号");
        this.privateKey = Objects.requireNonNull(privateKey, "商户私钥不能为空");
    }

    /**
     * 使用当前时间和随机 nonce 生成请求 Authorization 头。
     *
     * @param method HTTP 方法
     * @param canonicalUrl 请求路径（包含查询字符串）
     * @param body 请求 JSON 原文
     * @return 微信支付 Authorization 头
     * @author Henfon
     * @date 2026-08-31
     */
    public String buildAuthorization(String method, String canonicalUrl, String body) {
        return buildAuthorization(method, canonicalUrl, body, Instant.now().getEpochSecond(), UUID.randomUUID().toString());
    }

    /**
     * 使用指定时间和 nonce 生成确定性的 Authorization 头，便于单元测试。
     *
     * @param method HTTP 方法
     * @param canonicalUrl 请求路径（包含查询字符串）
     * @param body 请求 JSON 原文
     * @param timestamp Unix 秒时间戳
     * @param nonce 随机字符串
     * @return 微信支付 Authorization 头
     * @author Henfon
     * @date 2026-08-31
     */
    public String buildAuthorization(String method, String canonicalUrl, String body,
                                     long timestamp, String nonce) {
        String safeMethod = requireText(method, "HTTP 方法").toUpperCase();
        String safeUrl = requireText(canonicalUrl, "请求路径");
        String safeBody = body == null ? "" : body;
        String safeNonce = requireText(nonce, "nonce");
        String message = safeMethod + "\n" + safeUrl + "\n" + timestamp + "\n" + safeNonce + "\n" + safeBody + "\n";
        String signature = sign(message);
        return "WECHATPAY2-SHA256-RSA2048 mchid=\"" + merchantId + "\",nonce_str=\"" + safeNonce
                + "\",timestamp=\"" + timestamp + "\",serial_no=\"" + merchantSerialNumber
                + "\",signature=\"" + signature + "\"";
    }

    /**
     * 对微信 V3 五行签名串进行 RSA 签名。
     *
     * @param message 待签名文本
     * @return Base64 编码签名
     * @author Henfon
     * @date 2026-08-31
     */
    private String sign(String message) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception exception) {
            throw new WechatPayException("微信请求签名失败", exception);
        }
    }

    /**
     * 校验并返回非空文本。
     *
     * @param value 原始文本
     * @param name 字段名称
     * @return 原始文本
     * @author Henfon
     * @date 2026-08-31
     */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value;
    }
}
