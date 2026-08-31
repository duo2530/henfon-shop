package com.henfon.shop.payment.wechat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

/**
 * 微信支付 V3 渠道配置。
 *
 * @param enabled 是否启用真实渠道调用
 * @param mode 支付模式，首期支持 NATIVE
 * @param appId 微信 AppID
 * @param merchantId 商户号
 * @param merchantSerialNumber 商户证书序列号
 * @param apiV3Key API v3 密钥
 * @param merchantPrivateKeyPath 商户私钥文件路径
 * @param platformCertificatePath 微信支付平台证书文件路径
 * @param publicKeyId 微信支付平台公钥 ID（平台公钥模式）
 * @param publicKeyPath 微信支付平台公钥文件路径（平台公钥模式）
 * @param notifyUrl 支付回调地址
 * @param refundNotifyUrl 退款回调地址
 * @param apiBaseUrl API 根地址
 * @param connectTimeout HTTP 连接超时
 * @param readTimeout HTTP 读取超时
 * @author Henfon
 * @date 2026-08-31
 */
@ConfigurationProperties(prefix = "shop.payment.wechat")
public record WechatPayV3Properties(boolean enabled, String mode, String appId, String merchantId,
                                    String merchantSerialNumber, String apiV3Key,
                                    String merchantPrivateKeyPath, String platformCertificatePath,
                                    String publicKeyId, String publicKeyPath,
                                    String notifyUrl, String refundNotifyUrl, String apiBaseUrl,
                                    Duration connectTimeout, Duration readTimeout) {

    /**
     * 使用 Spring Boot 配置绑定创建微信支付 V3 配置。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @ConstructorBinding
    public WechatPayV3Properties(boolean enabled, String mode, String appId, String merchantId,
                                 String merchantSerialNumber, String apiV3Key,
                                 String merchantPrivateKeyPath, String platformCertificatePath,
                                 String publicKeyId, String publicKeyPath,
                                 String notifyUrl, String refundNotifyUrl, String apiBaseUrl,
                                 Duration connectTimeout, Duration readTimeout) {
        // 显式声明规范构造器，确保 Spring Boot 在存在兼容重载构造器时仍能完成配置绑定。
        this.enabled = enabled;
        this.mode = mode;
        this.appId = appId;
        this.merchantId = merchantId;
        this.merchantSerialNumber = merchantSerialNumber;
        this.apiV3Key = apiV3Key;
        this.merchantPrivateKeyPath = merchantPrivateKeyPath;
        this.platformCertificatePath = platformCertificatePath;
        this.publicKeyId = publicKeyId;
        this.publicKeyPath = publicKeyPath;
        this.notifyUrl = notifyUrl;
        this.refundNotifyUrl = refundNotifyUrl;
        this.apiBaseUrl = apiBaseUrl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    /**
     * 创建仅使用平台证书模式的兼容配置。
     *
     * @param enabled 是否启用真实渠道调用
     * @param mode 支付模式
     * @param appId 微信 AppID
     * @param merchantId 商户号
     * @param merchantSerialNumber 商户证书序列号
     * @param apiV3Key API v3 密钥
     * @param merchantPrivateKeyPath 商户私钥文件路径
     * @param platformCertificatePath 微信平台证书文件路径
     * @param notifyUrl 支付回调地址
     * @param refundNotifyUrl 退款回调地址
     * @param apiBaseUrl API 根地址
     * @param connectTimeout HTTP 连接超时
     * @param readTimeout HTTP 读取超时
     * @author Henfon
     * @date 2026-08-31
     */
    public WechatPayV3Properties(boolean enabled, String mode, String appId, String merchantId,
                                 String merchantSerialNumber, String apiV3Key,
                                 String merchantPrivateKeyPath, String platformCertificatePath,
                                 String notifyUrl, String refundNotifyUrl, String apiBaseUrl,
                                 Duration connectTimeout, Duration readTimeout) {
        this(enabled, mode, appId, merchantId, merchantSerialNumber, apiV3Key,
                merchantPrivateKeyPath, platformCertificatePath, null, null,
                notifyUrl, refundNotifyUrl, apiBaseUrl, connectTimeout, readTimeout);
    }
}
