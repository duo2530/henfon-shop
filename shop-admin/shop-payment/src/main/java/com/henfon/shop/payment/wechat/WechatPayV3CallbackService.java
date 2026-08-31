package com.henfon.shop.payment.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 微信支付 V3 回调安全服务。
 *
 * <p>控制器应先调用本服务完成请求头验签，再将解密后的业务 JSON 交给支付/退款应用服务，
 * 避免把未验签的请求体直接转换为支付成功状态。</p>
 *
 * @author Henfon
 * @date 2026-08-31
 */
public class WechatPayV3CallbackService {

    private final WechatPayV3Verifier verifier;
    private final long allowedSkewSeconds;
    private final String unavailableReason;

    /**
     * 创建回调安全服务。
     *
     * @param verifier V3 验签/解密器
     * @param allowedSkewSeconds 回调时间允许偏差
     * @author Henfon
     * @date 2026-08-31
     */
    public WechatPayV3CallbackService(WechatPayV3Verifier verifier, long allowedSkewSeconds) {
        this.verifier = java.util.Objects.requireNonNull(verifier, "V3 验签器不能为空");
        this.allowedSkewSeconds = Math.max(allowedSkewSeconds, 0);
        this.unavailableReason = null;
    }

    /**
     * 创建未配置时的安全失败回调服务。
     *
     * @param reason 未配置原因
     * @return 安全失败回调服务
     * @author Henfon
     * @date 2026-08-31
     */
    public static WechatPayV3CallbackService disabled(String reason) {
        return new WechatPayV3CallbackService(reason);
    }

    /**
     * 创建安全失败回调服务实例。
     *
     * @param reason 未配置原因
     * @author Henfon
     * @date 2026-08-31
     */
    private WechatPayV3CallbackService(String reason) {
        this.verifier = null;
        this.allowedSkewSeconds = 0;
        this.unavailableReason = reason == null || reason.isBlank() ? "微信支付 V3 回调未配置" : reason;
    }

    /**
     * 验签并解密支付或退款回调资源。
     *
     * @param timestamp 微信回调时间戳
     * @param nonce 微信回调随机串
     * @param signature 微信回调签名
     * @param body 回调请求原文
     * @param associatedData resource.associated_data
     * @param resourceNonce resource.nonce
     * @param ciphertext resource.ciphertext
     * @return 解密后的业务 JSON
     * @author Henfon
     * @date 2026-08-31
     */
    public String verifyAndDecrypt(String timestamp, String nonce, String signature, String body,
                                   String associatedData, String resourceNonce, String ciphertext) {
        ensureAvailable();
        if (!verifier.verify(timestamp, nonce, signature, body, allowedSkewSeconds)) {
            throw new WechatPayException("微信支付回调验签失败");
        }
        // 只有验签通过后才允许解密并交给业务层处理。
        return verifier.decryptResource(associatedData, resourceNonce, ciphertext);
    }

    /**
     * 验签并解密微信 V3 通知 envelope，返回 resource 明文。
     *
     * @param timestamp 微信回调时间戳
     * @param nonce 微信回调随机串
     * @param signature 微信回调签名
     * @param body 回调请求体原文
     * @param objectMapper JSON 解析器
     * @return 解密后的 resource JSON
     * @author Henfon
     * @date 2026-08-31
     */
    public String verifyAndDecryptNotification(String timestamp, String nonce, String signature,
                                                String body, ObjectMapper objectMapper) {
        ensureAvailable();
        if (objectMapper == null || body == null || body.isBlank()) {
            throw new WechatPayException("微信支付回调请求体不能为空");
        }
        try {
            JsonNode envelope = objectMapper.readTree(body);
            JsonNode resource = envelope == null ? null : envelope.get("resource");
            if (resource == null || resource.isNull()) {
                throw new WechatPayException("微信支付回调缺少 resource");
            }
            String associatedData = text(resource, "associated_data");
            String resourceNonce = text(resource, "nonce");
            String ciphertext = text(resource, "ciphertext");
            String algorithm = text(resource, "algorithm");
            if (algorithm != null && !algorithm.isBlank() && !"AEAD_AES_256_GCM".equals(algorithm)) {
                throw new WechatPayException("不支持的微信回调加密算法");
            }
            return verifyAndDecrypt(timestamp, nonce, signature, body, associatedData, resourceNonce, ciphertext);
        } catch (WechatPayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new WechatPayException("微信支付回调格式解析失败", exception);
        }
    }

    /**
     * 确保当前服务已加载平台公钥和 API v3 密钥。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    private void ensureAvailable() {
        if (verifier == null) {
            throw new WechatPayException(unavailableReason);
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
