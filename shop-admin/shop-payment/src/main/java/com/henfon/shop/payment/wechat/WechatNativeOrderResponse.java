package com.henfon.shop.payment.wechat;

/**
 * 微信 Native 下单结果。
 *
 * @param codeUrl 微信二维码链接
 * @param rawBody 渠道原始响应（仅供日志/诊断，不返回给门户）
 * @author Henfon
 * @date 2026-08-31
 */
public record WechatNativeOrderResponse(String codeUrl, String rawBody) {
}
