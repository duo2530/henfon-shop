package com.henfon.shop.payment.wechat;

import com.henfon.shop.payment.dto.PaymentOrderResponse;

/**
 * 本地支付单与微信 Native 二维码组合响应。
 *
 * @param paymentOrder 本地支付单
 * @param codeUrl 微信二维码链接
 * @author Henfon
 * @date 2026-08-31
 */
public record WechatNativeCheckoutResponse(PaymentOrderResponse paymentOrder, String codeUrl) {
}
