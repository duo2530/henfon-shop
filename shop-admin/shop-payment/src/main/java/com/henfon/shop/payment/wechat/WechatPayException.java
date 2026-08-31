package com.henfon.shop.payment.wechat;

/**
 * 微信支付适配器异常。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public class WechatPayException extends RuntimeException {

    /**
     * 创建渠道异常。
     *
     * @param message 异常信息
     * @author Henfon
     * @date 2026-08-31
     */
    public WechatPayException(String message) {
        super(message);
    }

    /**
     * 创建带原因的渠道异常。
     *
     * @param message 异常信息
     * @param cause 原始异常
     * @author Henfon
     * @date 2026-08-31
     */
    public WechatPayException(String message, Throwable cause) {
        super(message, cause);
    }
}
