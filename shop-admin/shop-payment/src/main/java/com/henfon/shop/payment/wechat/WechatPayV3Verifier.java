package com.henfon.shop.payment.wechat;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * 微信支付 V3 回调验签和通知资源解密器，支持平台证书公钥或平台公钥。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public final class WechatPayV3Verifier {

    private final PublicKey platformPublicKey;
    private final byte[] apiV3Key;

    /**
     * 创建回调验签器。
     *
     * @param platformPublicKey 微信支付平台验签公钥（可来自平台证书或 PEM 公钥）
     * @param apiV3Key API v3 密钥原文（必须 32 字节）
     * @author Henfon
     * @date 2026-08-31
     */
    public WechatPayV3Verifier(PublicKey platformPublicKey, String apiV3Key) {
        this.platformPublicKey = Objects.requireNonNull(platformPublicKey, "平台证书公钥不能为空");
        if (apiV3Key == null || apiV3Key.getBytes(StandardCharsets.UTF_8).length != 32) {
            throw new IllegalArgumentException("API v3 密钥必须是 32 字节");
        }
        this.apiV3Key = apiV3Key.getBytes(StandardCharsets.UTF_8).clone();
    }

    /**
     * 验证微信回调请求头签名并检查时间窗口，防止重放攻击。
     *
     * @param timestamp 微信回调时间戳
     * @param nonce 微信回调随机串
     * @param signature Base64 编码签名
     * @param body 回调请求体原文
     * @param allowedSkewSeconds 允许的时间偏差秒数
     * @return 签名是否有效
     * @author Henfon
     * @date 2026-08-31
     */
    public boolean verify(String timestamp, String nonce, String signature, String body, long allowedSkewSeconds) {
        if (isBlank(timestamp) || isBlank(nonce) || isBlank(signature) || body == null || allowedSkewSeconds < 0) {
            return false;
        }
        try {
            long seconds = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - seconds) > allowedSkewSeconds) {
                return false;
            }
            String message = timestamp + "\n" + nonce + "\n" + body + "\n";
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(platformPublicKey);
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * 使用默认五分钟时间窗口验证微信回调签名。
     *
     * @param timestamp 微信回调时间戳
     * @param nonce 微信回调随机串
     * @param signature Base64 编码签名
     * @param body 回调请求体原文
     * @return 签名是否有效
     * @author Henfon
     * @date 2026-08-31
     */
    public boolean verify(String timestamp, String nonce, String signature, String body) {
        return verify(timestamp, nonce, signature, body, 300);
    }

    /**
     * 解密微信回调中的 resource.ciphertext。
     *
     * @param associatedData 回调资源 associated_data
     * @param nonce 回调资源 nonce
     * @param ciphertext Base64 编码密文（末尾包含 16 字节 GCM tag）
     * @return 解密后的 JSON 原文
     * @author Henfon
     * @date 2026-08-31
     */
    public String decryptResource(String associatedData, String nonce, String ciphertext) {
        if (associatedData == null || isBlank(nonce) || isBlank(ciphertext)) {
            throw new IllegalArgumentException("微信回调资源字段不能为空");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(apiV3Key, "AES"),
                    new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new WechatPayException("微信回调资源解密失败", exception);
        }
    }

    /**
     * 判断文本是否为空白。
     *
     * @param value 待判断文本
     * @return 是否为空白
     * @author Henfon
     * @date 2026-08-31
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
