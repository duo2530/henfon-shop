package com.henfon.shop.payment.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;

/**
 * 微信支付 V3 适配器 Spring 配置。
 *
 * <p>未启用或商户证书、平台验签材料不完整时注入安全失败客户端，调用会明确报错，
 * 不会伪造支付成功。</p>
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Configuration
@EnableConfigurationProperties(WechatPayV3Properties.class)
public class WechatPayV3Configuration {

    /**
     * 创建微信支付客户端。
     *
     * @param properties 微信渠道配置
     * @param objectMapper JSON 序列化器
     * @return 真实 HTTP 客户端或安全失败客户端
     * @author Henfon
     * @date 2026-08-31
     */
    @Bean
    public WechatPayClient wechatPayClient(WechatPayV3Properties properties, ObjectMapper objectMapper) {
        if (!properties.enabled()) {
            return new DisabledWechatPayClient("微信支付未启用，请配置 shop.payment.wechat.enabled=true");
        }
        if (isBlank(properties.merchantId()) || isBlank(properties.merchantSerialNumber())
                || isBlank(properties.apiV3Key()) || isBlank(properties.merchantPrivateKeyPath())
                || !hasPlatformVerificationKey(properties) || isBlank(properties.apiBaseUrl())
                || properties.apiV3Key().getBytes(StandardCharsets.UTF_8).length != 32) {
            return new DisabledWechatPayClient("微信支付已启用但商户证书、平台验签材料或 API 密钥配置不完整");
        }
        try {
            // 启动时验证平台证书或平台公钥可读，避免真实渠道回调验签时才暴露配置错误。
            loadPlatformVerificationKey(properties);
            WechatPayV3Signer signer = new WechatPayV3Signer(properties.merchantId(),
                    properties.merchantSerialNumber(), WechatPayKeyLoader.loadPrivateKey(properties.merchantPrivateKeyPath()));
            // 平台验签材料在回调控制器接入时通过同一配置加载，HTTP 客户端只需要商户私钥签名。
            Duration connectTimeout = properties.connectTimeout() == null ? Duration.ofSeconds(3) : properties.connectTimeout();
            HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
            return new WechatPayV3HttpClient(httpClient, objectMapper, signer, properties.apiBaseUrl(),
                    properties.readTimeout());
        } catch (WechatPayException | IllegalArgumentException exception) {
            return new DisabledWechatPayClient("微信支付证书或平台公钥加载失败，未执行真实请求: " + exception.getMessage());
        }
    }

    /**
     * 创建微信 V3 回调安全服务。
     *
     * <p>配置缺失时返回安全失败实例，保持应用可启动但拒绝未经平台公钥验证的回调。</p>
     *
     * @param properties 微信渠道配置
     * @param allowedSkewSeconds 回调时间允许偏差秒数
     * @return 回调验签和解密服务
     * @author Henfon
     * @date 2026-08-31
     */
    @Bean
    public WechatPayV3CallbackService wechatPayV3CallbackService(WechatPayV3Properties properties,
                                                                  @Value("${shop.payment.wechat.callback-allowed-skew-seconds:300}") long allowedSkewSeconds) {
        if (!properties.enabled()) {
            return WechatPayV3CallbackService.disabled("微信支付未启用，拒绝处理 V3 回调");
        }
        if (isBlank(properties.apiV3Key()) || properties.apiV3Key().getBytes(StandardCharsets.UTF_8).length != 32
                || !hasPlatformVerificationKey(properties)) {
            return WechatPayV3CallbackService.disabled("微信支付 V3 回调平台公钥或 API v3 密钥配置不完整");
        }
        try {
            WechatPayV3Verifier verifier = new WechatPayV3Verifier(
                    loadPlatformVerificationKey(properties), properties.apiV3Key());
            return new WechatPayV3CallbackService(verifier, allowedSkewSeconds);
        } catch (WechatPayException | IllegalArgumentException exception) {
            return WechatPayV3CallbackService.disabled("微信支付 V3 回调验签材料加载失败: " + exception.getMessage());
        }
    }

    /**
     * 判断是否配置了平台证书或平台公钥验签材料。
     *
     * <p>优先使用平台证书，保证已有证书模式行为不变；仅当证书路径为空时才回退到平台公钥模式。</p>
     *
     * @param properties 微信渠道配置
     * @return 是否存在可用的验签材料配置
     * @author Henfon
     * @date 2026-08-31
     */
    static boolean hasPlatformVerificationKey(WechatPayV3Properties properties) {
        return !isBlankValue(properties.platformCertificatePath())
                || !isBlankValue(properties.publicKeyPath());
    }

    /**
     * 按配置加载平台验签公钥，兼容传统平台证书和平台公钥两种模式。
     *
     * @param properties 微信渠道配置
     * @return 平台验签公钥
     * @author Henfon
     * @date 2026-08-31
     */
    static PublicKey loadPlatformVerificationKey(WechatPayV3Properties properties) {
        if (!isBlankValue(properties.platformCertificatePath())) {
            return WechatPayKeyLoader.loadPlatformPublicKey(properties.platformCertificatePath());
        }
        if (!isBlankValue(properties.publicKeyPath())) {
            if (isBlankValue(properties.publicKeyId())) {
                throw new WechatPayException("配置平台公钥路径时必须同时配置 public-key-id");
            }
            return WechatPayKeyLoader.loadPublicKey(properties.publicKeyPath());
        }
        throw new WechatPayException("微信支付必须配置平台证书路径或平台公钥路径");
    }

    /**
     * 判断配置文本是否为空白。
     *
     * @param value 配置文本
     * @return 是否为空白
     * @author Henfon
     * @date 2026-08-31
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 判断配置文本是否为空白，供静态配置校验方法复用。
     *
     * @param value 配置文本
     * @return 是否为空白
     * @author Henfon
     * @date 2026-08-31
     */
    private static boolean isBlankValue(String value) {
        return value == null || value.isBlank();
    }
}
