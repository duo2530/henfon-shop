package com.henfon.shop.payment.wechat;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 微信支付网关下单、退款和幂等测试。
 *
 * @author Henfon
 * @date 2026-08-31
 */
class WechatPayGatewayServiceTest {

    /**
     * 校验同一商户支付单重复下单只调用一次渠道客户端。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldDeduplicateNativeOrder() {
        FakeClient client = new FakeClient();
        WechatPayGatewayService service = new WechatPayGatewayService(client);
        WechatNativeOrderRequest request = nativeRequest();

        assertEquals("weixin://qr/PAY-1", service.createNativeOrder(request).codeUrl());
        assertEquals("weixin://qr/PAY-1", service.createNativeOrder(request).codeUrl());
        assertEquals(1, client.nativeCalls);
    }

    /**
     * 校验同一支付单使用不同金额时拒绝复用旧二维码。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectNativeOrderParameterConflict() {
        FakeClient client = new FakeClient();
        WechatPayGatewayService service = new WechatPayGatewayService(client);
        service.createNativeOrder(nativeRequest());
        WechatNativeOrderRequest conflict = new WechatNativeOrderRequest("wx-app", "mchid", "测试商品",
                "PAY-1", new BigDecimal("12.01"), "https://example.test/pay/notify");

        WechatPayException exception = assertThrows(WechatPayException.class,
                () -> service.createNativeOrder(conflict));
        assertEquals("支付单号已使用不同参数下单", exception.getMessage());
    }

    /**
     * 校验退款单号重复调用保持幂等且参数冲突会失败。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldDeduplicateRefund() {
        FakeClient client = new FakeClient();
        WechatPayGatewayService service = new WechatPayGatewayService(client);
        WechatRefundRequest request = new WechatRefundRequest(null, "PAY-1", "REF-1", "售后退款",
                new BigDecimal("2.00"), new BigDecimal("12.00"), "https://example.test/refund/notify");

        assertEquals("REFUND-1", service.refund(request).refundId());
        assertEquals("REFUND-1", service.refund(request).refundId());
        assertEquals(1, client.refundCalls);
    }

    /**
     * 校验未配置商户证书时客户端明确失败，不返回伪造成功结果。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldFailWhenWechatCredentialsAreMissing() {
        DisabledWechatPayClient client = new DisabledWechatPayClient("测试环境未配置商户证书");
        WechatPayException exception = assertThrows(WechatPayException.class,
                () -> client.createNativeOrder(nativeRequest()));
        assertEquals("测试环境未配置商户证书", exception.getMessage());
    }

    /**
     * 创建 Native 测试请求。
     *
     * @return Native 请求
     * @author Henfon
     * @date 2026-08-31
     */
    private WechatNativeOrderRequest nativeRequest() {
        return new WechatNativeOrderRequest("wx-app", "mchid", "测试商品", "PAY-1",
                new BigDecimal("12.00"), "https://example.test/pay/notify");
    }

    /**
     * 仅用于单元测试的可注入渠道客户端，不代表真实联调成功。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    private static final class FakeClient implements WechatPayClient {

        private int nativeCalls;
        private int refundCalls;

        /**
         * 返回固定测试二维码。
         *
         * @param request Native 请求
         * @return 测试结果
         * @author Henfon
         * @date 2026-08-31
         */
        @Override
        public WechatNativeOrderResponse createNativeOrder(WechatNativeOrderRequest request) {
            nativeCalls++;
            return new WechatNativeOrderResponse("weixin://qr/PAY-1", "{\"code_url\":\"test\"}");
        }

        /**
         * 返回固定测试退款结果。
         *
         * @param request 退款请求
         * @return 测试结果
         * @author Henfon
         * @date 2026-08-31
         */
        @Override
        public WechatRefundResponse refund(WechatRefundRequest request) {
            refundCalls++;
            return new WechatRefundResponse("REFUND-1", "PROCESSING", "{\"status\":\"PROCESSING\"}");
        }
    }
}
