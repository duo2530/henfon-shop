package com.henfon.shop.payment.wechat;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 微信支付 V3 签名、验签和回调解密测试。
 *
 * @author Henfon
 * @date 2026-08-31
 */
class WechatPayV3SignerVerifierTest {

    /**
     * 校验请求签名可由平台公钥验证，且篡改请求体后验证失败。
     *
     * @throws Exception 测试密钥生成异常
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldSignAndVerifyRequest() throws Exception {
        KeyPair keyPair = keyPair();
        WechatPayV3Signer signer = new WechatPayV3Signer("mchid", "serial", keyPair.getPrivate());
        String body = "{\"amount\":100}";
        long timestamp = Instant.now().getEpochSecond();
        String authorization = signer.buildAuthorization("POST", "/v3/pay/transactions/native", body,
                timestamp, "nonce-123");
        assertTrue(authorization.startsWith("WECHATPAY2-SHA256-RSA2048 mchid=\"mchid\""));
        // 回调验签使用“时间戳\n随机串\n请求体\n”格式，与请求 Authorization 签名串不同。
        Signature callbackSigner = Signature.getInstance("SHA256withRSA");
        callbackSigner.initSign(keyPair.getPrivate());
        callbackSigner.update((timestamp + "\nnonce-123\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(callbackSigner.sign());

        WechatPayV3Verifier verifier = new WechatPayV3Verifier(keyPair.getPublic(), "12345678901234567890123456789012");
        assertTrue(verifier.verify(Long.toString(timestamp), "nonce-123", signature, body, 300));
        assertFalse(verifier.verify(Long.toString(timestamp), "nonce-123", signature, body + "x", 300));
    }

    /**
     * 校验 API v3 密钥可以解密 AES/GCM 回调资源。
     *
     * @throws Exception 测试加密异常
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldDecryptNotificationResource() throws Exception {
        String apiV3Key = "12345678901234567890123456789012";
        String associatedData = "transaction";
        String nonce = "123456789012";
        String plaintext = "{\"out_trade_no\":\"PAY-1\"}";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(apiV3Key.getBytes(StandardCharsets.UTF_8), "AES"),
                new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        String ciphertext = Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));

        WechatPayV3Verifier verifier = new WechatPayV3Verifier(keyPair().getPublic(), apiV3Key);
        assertEquals(plaintext, verifier.decryptResource(associatedData, nonce, ciphertext));
    }

    /**
     * 生成测试 RSA 密钥对。
     *
     * @return RSA 密钥对
     * @throws Exception 密钥生成异常
     * @author Henfon
     * @date 2026-08-31
     */
    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
