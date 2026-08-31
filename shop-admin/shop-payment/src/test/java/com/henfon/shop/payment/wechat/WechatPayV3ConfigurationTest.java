package com.henfon.shop.payment.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 微信支付 V3 配置兼容性测试。
 *
 * @author Henfon
 * @date 2026-08-31
 */
class WechatPayV3ConfigurationTest {

    /**
     * 校验平台证书为空时，可以从 application-dev.yml 使用的平台公钥 PEM 加载验签公钥。
     *
     * @param tempDir JUnit 临时目录
     * @throws Exception 文件写入和密钥生成异常
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldLoadPublicKeyWhenCertificatePathIsMissing(@TempDir Path tempDir) throws Exception {
        KeyPair keyPair = keyPair();
        Path publicKeyPath = writePem(tempDir.resolve("pub_key.pem"), "PUBLIC KEY", keyPair.getPublic().getEncoded());
        WechatPayV3Properties properties = properties(publicKeyPath.toString(), "PUB_KEY_ID_TEST");

        assertArrayEquals(keyPair.getPublic().getEncoded(),
                WechatPayV3Configuration.loadPlatformVerificationKey(properties).getEncoded());
    }

    /**
     * 校验平台公钥路径配置缺少公钥 ID 时拒绝启动真实客户端。
     *
     * @param tempDir JUnit 临时目录
     * @throws Exception 文件写入和密钥生成异常
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectPublicKeyWithoutPublicKeyId(@TempDir Path tempDir) throws Exception {
        KeyPair keyPair = keyPair();
        Path publicKeyPath = writePem(tempDir.resolve("pub_key.pem"), "PUBLIC KEY", keyPair.getPublic().getEncoded());
        WechatPayV3Properties properties = properties(publicKeyPath.toString(), "");

        assertThrows(WechatPayException.class,
                () -> WechatPayV3Configuration.loadPlatformVerificationKey(properties));
    }

    /**
     * 校验平台公钥模式通过全部证书配置校验后创建真实 HTTP 客户端。
     *
     * @param tempDir JUnit 临时目录
     * @throws Exception 文件写入和密钥生成异常
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldCreateHttpClientWithPublicKeyMode(@TempDir Path tempDir) throws Exception {
        KeyPair keyPair = keyPair();
        Path publicKeyPath = writePem(tempDir.resolve("pub_key.pem"), "PUBLIC KEY", keyPair.getPublic().getEncoded());
        Path privateKeyPath = writePem(tempDir.resolve("apiclient_key.pem"), "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        WechatPayV3Properties properties = properties(publicKeyPath.toString(), "PUB_KEY_ID_TEST");
        properties = new WechatPayV3Properties(properties.enabled(), properties.mode(), properties.appId(),
                properties.merchantId(), properties.merchantSerialNumber(), properties.apiV3Key(),
                privateKeyPath.toString(), properties.platformCertificatePath(), properties.publicKeyId(),
                properties.publicKeyPath(), properties.notifyUrl(), properties.refundNotifyUrl(),
                properties.apiBaseUrl(), properties.connectTimeout(), properties.readTimeout());

        assertInstanceOf(WechatPayV3HttpClient.class,
                new WechatPayV3Configuration().wechatPayClient(properties, new ObjectMapper()));
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

    /**
     * 创建平台公钥模式配置样例。
     *
     * @param publicKeyPath 平台公钥文件路径
     * @param publicKeyId 平台公钥 ID
     * @return 微信支付配置
     * @author Henfon
     * @date 2026-08-31
     */
    private WechatPayV3Properties properties(String publicKeyPath, String publicKeyId) {
        return new WechatPayV3Properties(true, "NATIVE", "wx-app", "mchid", "merchant-serial",
                "12345678901234567890123456789012", "unused-private-key.pem", "", publicKeyId,
                publicKeyPath, "https://example.test/pay/notify", "https://example.test/refund/notify",
                "https://api.mch.weixin.qq.com", null, null);
    }

    /**
     * 将 DER 编码写成 PEM 文件。
     *
     * @param path 文件路径
     * @param type PEM 类型
     * @param encoded DER 编码
     * @return 写入后的文件路径
     * @throws Exception 文件写入异常
     * @author Henfon
     * @date 2026-08-31
     */
    private Path writePem(Path path, String type, byte[] encoded) throws Exception {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(encoded);
        return Files.writeString(path, "-----BEGIN " + type + "-----\n" + body
                + "\n-----END " + type + "-----\n", StandardCharsets.US_ASCII);
    }
}
