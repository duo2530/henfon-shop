package com.henfon.shop.payment.wechat;

/**
 * 未配置商户证书时使用的安全失败客户端。
 *
 * <p>该实现故意不返回模拟成功结果，避免开发环境误把支付状态推进为成功。</p>
 *
 * @author Henfon
 * @date 2026-08-31
 */
public final class DisabledWechatPayClient implements WechatPayClient {

    private final String reason;

    /**
     * 创建安全失败客户端。
     *
     * @param reason 禁用原因
     * @author Henfon
     * @date 2026-08-31
     */
    public DisabledWechatPayClient(String reason) {
        this.reason = reason == null || reason.isBlank() ? "微信支付客户端未配置" : reason;
    }

    /**
     * 拒绝发送 Native 下单请求。
     *
     * @param request Native 下单请求
     * @return 不返回
     * @author Henfon
     * @date 2026-08-31
     */
    @Override
    public WechatNativeOrderResponse createNativeOrder(WechatNativeOrderRequest request) {
        throw new WechatPayException(reason);
    }

    /**
     * 拒绝发送退款请求。
     *
     * @param request 退款请求
     * @return 不返回
     * @author Henfon
     * @date 2026-08-31
     */
    @Override
    public WechatRefundResponse refund(WechatRefundRequest request) {
        throw new WechatPayException(reason);
    }
}
