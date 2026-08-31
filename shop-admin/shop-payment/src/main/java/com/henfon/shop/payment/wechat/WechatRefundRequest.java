package com.henfon.shop.payment.wechat;

import java.math.BigDecimal;

/**
 * 微信 V3 原路退款参数。
 *
 * @param transactionNo 微信支付交易号，可为空但商户订单号必须提供
 * @param outTradeNo 商户支付单号
 * @param outRefundNo 商户退款单号，作为幂等键
 * @param reason 退款原因
 * @param refundAmount 退款金额（人民币）
 * @param totalAmount 原支付金额（人民币）
 * @param notifyUrl 退款回调地址
 * @author Henfon
 * @date 2026-08-31
 */
public record WechatRefundRequest(String transactionNo, String outTradeNo, String outRefundNo,
                                  String reason, BigDecimal refundAmount, BigDecimal totalAmount,
                                  String notifyUrl) {

    /**
     * 校验退款金额和商户幂等字段。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    public WechatRefundRequest {
        if (isBlank(outTradeNo) || isBlank(outRefundNo) || isBlank(notifyUrl)) {
            throw new IllegalArgumentException("微信退款参数不能为空");
        }
        if (refundAmount == null || refundAmount.signum() <= 0
                || totalAmount == null || totalAmount.signum() <= 0
                || refundAmount.compareTo(totalAmount) > 0) {
            throw new IllegalArgumentException("微信退款金额不合法");
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
