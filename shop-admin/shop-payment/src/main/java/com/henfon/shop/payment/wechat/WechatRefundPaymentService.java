package com.henfon.shop.payment.wechat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.payment.dto.PaymentRefundCreateRequest;
import com.henfon.shop.payment.dto.PaymentRefundResponse;
import com.henfon.shop.payment.entity.PaymentOrder;
import com.henfon.shop.payment.mapper.PaymentOrderMapper;
import com.henfon.shop.payment.service.PaymentRefundService;
import org.springframework.stereotype.Service;

/**
 * 微信退款可选提交入口。
 *
 * <p>先复用退款应用服务完成可退金额和本地幂等校验，再调用微信渠道；渠道调用失败时保留待退款单，
 * 便于后台重试，不会把本地状态伪造为成功。</p>
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class WechatRefundPaymentService {

    private final PaymentRefundService refundService;
    private final PaymentOrderMapper paymentOrderMapper;
    private final WechatPayGatewayService gatewayService;
    private final WechatPayV3Properties properties;

    /**
     * 创建微信退款提交入口服务。
     *
     * @param refundService 本地退款单服务
     * @param paymentOrderMapper 支付单数据访问对象
     * @param gatewayService 微信渠道网关
     * @param properties 微信渠道配置
     * @author Henfon
     * @date 2026-08-31
     */
    public WechatRefundPaymentService(PaymentRefundService refundService, PaymentOrderMapper paymentOrderMapper,
                                      WechatPayGatewayService gatewayService, WechatPayV3Properties properties) {
        this.refundService = refundService;
        this.paymentOrderMapper = paymentOrderMapper;
        this.gatewayService = gatewayService;
        this.properties = properties;
    }

    /**
     * 创建本地退款单并提交微信原路退款。
     *
     * @param request 退款申请
     * @return 本地退款单响应
     * @author Henfon
     * @date 2026-08-31
     */
    public PaymentRefundResponse createAndSubmit(PaymentRefundCreateRequest request) {
        PaymentRefundResponse refund = refundService.create(request);
        PaymentOrder paymentOrder = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getPaymentNo, refund.paymentNo()).last("LIMIT 1"));
        if (paymentOrder == null || paymentOrder.getAmount() == null) {
            throw new WechatPayException("退款对应的支付单不存在，未发送渠道请求");
        }
        ensureConfigured();
        // 渠道退款单号使用本地退款单号，重复提交由网关幂等保护。
        gatewayService.refund(new WechatRefundRequest(paymentOrder.getTransactionNo(), paymentOrder.getPaymentNo(),
                refund.refundNo(), refund.reason(), refund.amount(), paymentOrder.getAmount(),
                properties.refundNotifyUrl()));
        return refund;
    }

    /**
     * 校验退款回调地址配置。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    private void ensureConfigured() {
        if (isBlank(properties.refundNotifyUrl())) {
            throw new WechatPayException("微信退款回调地址未配置，未发送渠道请求");
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
