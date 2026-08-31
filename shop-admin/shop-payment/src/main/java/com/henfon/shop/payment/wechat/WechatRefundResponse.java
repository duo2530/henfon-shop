package com.henfon.shop.payment.wechat;

/**
 * 微信退款结果。
 *
 * @param refundId 微信退款单号
 * @param status 微信退款状态
 * @param rawBody 渠道原始响应（仅供日志/诊断）
 * @author Henfon
 * @date 2026-08-31
 */
public record WechatRefundResponse(String refundId, String status, String rawBody) {
}
