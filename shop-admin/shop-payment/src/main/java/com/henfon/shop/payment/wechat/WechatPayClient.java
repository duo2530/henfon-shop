package com.henfon.shop.payment.wechat;

/**
 * 微信支付渠道客户端抽象，隔离业务服务与 HTTP/证书实现。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public interface WechatPayClient {

    /**
     * 创建微信 Native 支付订单。
     *
     * @param request Native 下单请求
     * @return 微信返回的二维码链接
     * @author Henfon
     * @date 2026-08-31
     */
    WechatNativeOrderResponse createNativeOrder(WechatNativeOrderRequest request);

    /**
     * 创建微信原路退款订单。
     *
     * @param request 退款请求
     * @return 微信退款结果
     * @author Henfon
     * @date 2026-08-31
     */
    WechatRefundResponse refund(WechatRefundRequest request);
}
