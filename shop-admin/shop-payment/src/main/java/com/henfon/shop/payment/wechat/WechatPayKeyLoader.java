package com.henfon.shop.payment.wechat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 微信支付商户私钥、平台证书和平台公钥加载工具。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public final class WechatPayKeyLoader {

    private WechatPayKeyLoader() {
    }

    /**
     * 从 PEM 文件加载 PKCS#8 RSA 私钥。
     *
     * @param path 私钥文件路径
     * @return RSA 私钥
     * @author Henfon
     * @date 2026-08-31
     */
    public static PrivateKey loadPrivateKey(String path) {
        try {
            String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] encoded = Base64.getDecoder().decode(pem);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new WechatPayException("加载微信商户私钥失败", exception);
        }
    }

    /**
     * 从 X.509 PEM 文件加载微信支付平台公钥。
     *
     * @param path 平台证书文件路径
     * @return 平台证书公钥
     * @author Henfon
     * @date 2026-08-31
     */
    public static PublicKey loadPlatformPublicKey(String path) {
        try (InputStream inputStream = Files.newInputStream(Path.of(path))) {
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(inputStream);
            return certificate.getPublicKey();
        } catch (Exception exception) {
            throw new WechatPayException("加载微信平台证书失败", exception);
        }
    }

    /**
     * 从 PEM 文件加载微信支付平台公钥（SubjectPublicKeyInfo，通常为 BEGIN PUBLIC KEY）。
     *
     * @param path 平台公钥文件路径
     * @return 平台公钥
     * @author Henfon
     * @date 2026-08-31
     */
    public static PublicKey loadPublicKey(String path) {
        try {
            String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] encoded = Base64.getDecoder().decode(pem);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new WechatPayException("加载微信支付平台公钥失败", exception);
        }
    }

    /**
     * 按配置加载平台验签公钥，优先使用传统平台证书，证书为空时回退平台公钥。
     *
     * @param certificatePath 平台证书文件路径，可为空
     * @param publicKeyPath 平台公钥文件路径，可为空
     * @return 平台验签公钥
     * @author Henfon
     * @date 2026-08-31
     */
    public static PublicKey loadPlatformPublicKey(String certificatePath, String publicKeyPath) {
        if (certificatePath != null && !certificatePath.isBlank()) {
            return loadPlatformPublicKey(certificatePath);
        }
        if (publicKeyPath != null && !publicKeyPath.isBlank()) {
            return loadPublicKey(publicKeyPath);
        }
        throw new WechatPayException("微信支付必须配置平台证书路径或平台公钥路径");
    }
}
