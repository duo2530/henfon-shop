package com.henfon.shop.payment.wechat;

import java.math.BigDecimal;

/**
 * 微信 Native V3 下单参数。
 *
 * @param appId 微信 AppID
 * @param merchantId 商户号
 * @param description 商品描述
 * @param outTradeNo 商户订单号
 * @param amount 支付金额（人民币）
 * @param notifyUrl 支付回调地址
 * @author Henfon
 * @date 2026-08-31
 */
public record WechatNativeOrderRequest(String appId, String merchantId, String description,
                                       String outTradeNo, BigDecimal amount, String notifyUrl) {

    /**
     * 校验 Native 下单字段，避免把无效金额发送给支付平台。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    public WechatNativeOrderRequest {
        if (isBlank(appId) || isBlank(merchantId) || isBlank(description)
                || isBlank(outTradeNo) || isBlank(notifyUrl)) {
            throw new IllegalArgumentException("微信 Native 下单参数不能为空");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("微信 Native 下单金额必须大于0");
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
