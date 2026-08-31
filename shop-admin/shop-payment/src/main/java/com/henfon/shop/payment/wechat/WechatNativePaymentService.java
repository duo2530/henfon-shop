package com.henfon.shop.payment.wechat;

import com.henfon.shop.payment.dto.PaymentCreateRequest;
import com.henfon.shop.payment.dto.PaymentOrderResponse;
import com.henfon.shop.payment.service.PaymentService;
import org.springframework.stereotype.Service;

/**
 * 微信 Native 支付可选入口。
 *
 * <p>该服务先创建本地支付单，再调用可注入渠道客户端；渠道未配置时安全失败，
 * 本地支付单仍可由原有接口查询和重试，不会被伪造为支付成功。</p>
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class WechatNativePaymentService {

    private final PaymentService paymentService;
    private final WechatPayGatewayService gatewayService;
    private final WechatPayV3Properties properties;

    /**
     * 创建微信 Native 支付入口服务。
     *
     * @param paymentService 本地支付单服务
     * @param gatewayService 微信渠道网关
     * @param properties 微信渠道配置
     * @author Henfon
     * @date 2026-08-31
     */
    public WechatNativePaymentService(PaymentService paymentService, WechatPayGatewayService gatewayService,
                                      WechatPayV3Properties properties) {
        this.paymentService = paymentService;
        this.gatewayService = gatewayService;
        this.properties = properties;
    }

    /**
     * 创建本地支付单并获取微信 Native 二维码。
     *
     * @param memberId 会员ID
     * @param orderId 订单ID
     * @return 本地支付单与二维码
     * @author Henfon
     * @date 2026-08-31
     */
    public WechatNativeCheckoutResponse create(Long memberId, Long orderId) {
        PaymentOrderResponse paymentOrder = paymentService.create(memberId, orderId,
                new PaymentCreateRequest("WECHAT_NATIVE"));
        ensureConfigured();
        WechatNativeOrderResponse channelResponse = gatewayService.createNativeOrder(new WechatNativeOrderRequest(
                properties.appId(), properties.merchantId(), "商城订单 " + paymentOrder.orderNo(),
                paymentOrder.paymentNo(), paymentOrder.amount(), properties.notifyUrl()));
        return new WechatNativeCheckoutResponse(paymentOrder, channelResponse.codeUrl());
    }

    /**
     * 校验 Native 下单必要配置。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    private void ensureConfigured() {
        if (isBlank(properties.appId()) || isBlank(properties.merchantId()) || isBlank(properties.notifyUrl())) {
            throw new WechatPayException("微信 Native 下单配置不完整，未发送渠道请求");
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
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
