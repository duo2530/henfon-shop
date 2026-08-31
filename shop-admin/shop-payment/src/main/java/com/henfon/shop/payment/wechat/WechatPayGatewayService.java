package com.henfon.shop.payment.wechat;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信支付网关应用服务，提供渠道调用和进程内幂等保护。
 *
 * <p>数据库支付单仍是最终幂等依据；此处用商户单号抑制同一实例内的重复 HTTP 请求，
 * 发生参数冲突时直接失败而不是复用错误结果。</p>
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class WechatPayGatewayService {

    private final WechatPayClient client;
    private final Map<String, NativeCall> nativeCalls = new ConcurrentHashMap<>();
    private final Map<String, RefundCall> refundCalls = new ConcurrentHashMap<>();

    /**
     * 创建微信网关服务。
     *
     * @param client 可注入的微信渠道客户端
     * @author Henfon
     * @date 2026-08-31
     */
    public WechatPayGatewayService(WechatPayClient client) {
        this.client = client;
    }

    /**
     * 幂等创建 Native 支付订单。
     *
     * @param request Native 下单请求
     * @return Native 下单结果
     * @author Henfon
     * @date 2026-08-31
     */
    public WechatNativeOrderResponse createNativeOrder(WechatNativeOrderRequest request) {
        NativeCall call = new NativeCall(request, null);
        synchronized (nativeCalls) {
            NativeCall existing = nativeCalls.get(request.outTradeNo());
            if (existing != null) {
                ensureSame(existing.request(), request, "支付单号已使用不同参数下单");
                return existing.response();
            }
            WechatNativeOrderResponse response = client.createNativeOrder(request);
            if (response == null || response.codeUrl() == null || response.codeUrl().isBlank()) {
                throw new WechatPayException("微信 Native 下单未返回二维码链接");
            }
            nativeCalls.put(request.outTradeNo(), new NativeCall(request, response));
            return response;
        }
    }

    /**
     * 幂等创建原路退款订单并校验退款单号参数一致性。
     *
     * @param request 退款请求
     * @return 退款结果
     * @author Henfon
     * @date 2026-08-31
     */
    public WechatRefundResponse refund(WechatRefundRequest request) {
        synchronized (refundCalls) {
            RefundCall existing = refundCalls.get(request.outRefundNo());
            if (existing != null) {
                ensureSame(existing.request(), request, "退款单号已使用不同参数退款");
                return existing.response();
            }
            WechatRefundResponse response = client.refund(request);
            if (response == null || response.refundId() == null || response.refundId().isBlank()) {
                throw new WechatPayException("微信退款未返回退款单号");
            }
            refundCalls.put(request.outRefundNo(), new RefundCall(request, response));
            return response;
        }
    }

    /**
     * 比较幂等键对应的请求，阻止同一商户单号被复用。
     *
     * @param original 首次请求
     * @param current 当前请求
     * @param message 冲突错误信息
     * @author Henfon
     * @date 2026-08-31
     */
    private void ensureSame(Object original, Object current, String message) {
        if (!original.equals(current)) {
            throw new WechatPayException(message);
        }
    }

    /**
     * Native 调用缓存值。
     *
     * @param request 原始请求
     * @param response 渠道结果
     * @author Henfon
     * @date 2026-08-31
     */
    private record NativeCall(WechatNativeOrderRequest request, WechatNativeOrderResponse response) {
    }

    /**
     * 退款调用缓存值。
     *
     * @param request 原始请求
     * @param response 渠道结果
     * @author Henfon
     * @date 2026-08-31
     */
    private record RefundCall(WechatRefundRequest request, WechatRefundResponse response) {
    }
}
