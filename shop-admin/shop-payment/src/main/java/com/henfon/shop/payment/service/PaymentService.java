package com.henfon.shop.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.payment.dto.PaymentCreateRequest;
import com.henfon.shop.payment.dto.PaymentNotifyRequest;
import com.henfon.shop.payment.dto.PaymentOrderResponse;
import com.henfon.shop.payment.entity.PaymentOrder;
import com.henfon.shop.payment.mapper.PaymentOrderMapper;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.service.TradeOrderService;
import com.henfon.shop.trade.service.TradeOrderStateMachine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付单应用服务，负责支付单生命周期和异步通知幂等处理。
 *
 * <p>当前实现不直接调用微信等第三方网关，先提供支付单、订单状态联动和 Outbox 基础能力。</p>
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Service
public class PaymentService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_SUCCEEDED = 2;
    private static final int STATUS_CLOSED = 3;
    private static final int STATUS_FAILED = 4;

    private final PaymentOrderMapper paymentOrderMapper;
    private final TradeOrderService tradeOrderService;
    private final int timeoutMinutes;

    /**
     * 创建支付应用服务。
     *
     * @param paymentOrderMapper 支付单数据访问对象
     * @param tradeOrderService 交易订单应用服务
     * @param timeoutMinutes 支付单有效期（分钟）
     * @author Henfon
     * @date 2026-08-30
     */
    public PaymentService(PaymentOrderMapper paymentOrderMapper, TradeOrderService tradeOrderService,
                          @Value("${shop.trade.order-payment-timeout-minutes:30}") int timeoutMinutes) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.tradeOrderService = tradeOrderService;
        this.timeoutMinutes = Math.max(timeoutMinutes, 1);
    }

    /**
     * 为会员订单创建或幂等返回支付单。
     *
     * @param memberId 当前会员ID
     * @param orderId 订单ID
     * @param request 支付渠道请求
     * @return 支付单响应
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public PaymentOrderResponse create(Long memberId, Long orderId, PaymentCreateRequest request) {
        TradeOrder order = requireMemberOrder(memberId, orderId);
        if (!Integer.valueOf(0).equals(order.getPaymentStatus())
                || !Integer.valueOf(TradeOrderStateMachine.STATUS_PENDING_PAYMENT).equals(order.getOrderStatus())) {
            throw new BusinessException("PAYMENT_ORDER_STATUS_INVALID", "只有待付款订单允许创建支付单");
        }
        String channel = request.channel().trim().toUpperCase();
        PaymentOrder existing = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderId, orderId)
                .eq(PaymentOrder::getChannel, channel)
                .last("LIMIT 1 FOR UPDATE"));
        if (existing != null) {
            if (existing.getStatus() == STATUS_CLOSED || existing.getStatus() == STATUS_FAILED) {
                throw new BusinessException("PAYMENT_ORDER_CLOSED", "该订单支付单已关闭，请重新创建订单");
            }
            return PaymentOrderResponse.from(existing);
        }
        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setPaymentNo("PAY" + IdWorker.getIdStr());
        paymentOrder.setOrderId(order.getId());
        paymentOrder.setOrderNo(order.getOrderNo());
        paymentOrder.setMemberId(order.getMemberId());
        paymentOrder.setChannel(channel);
        // 尚未调用第三方收银台时保持待支付，渠道适配器发起请求后再推进为支付中。
        paymentOrder.setStatus(STATUS_PENDING);
        paymentOrder.setAmount(order.getPayableAmount());
        paymentOrder.setExpireAt(LocalDateTime.now().plusMinutes(timeoutMinutes));
        paymentOrderMapper.insert(paymentOrder);
        return PaymentOrderResponse.from(paymentOrder);
    }

    /**
     * 查询会员支付单。
     *
     * @param memberId 当前会员ID
     * @param paymentNo 支付单号
     * @return 支付单响应
     * @author Henfon
     * @date 2026-08-30
     */
    public PaymentOrderResponse get(Long memberId, String paymentNo) {
        PaymentOrder paymentOrder = requirePayment(paymentNo);
        if (memberId == null || !memberId.equals(paymentOrder.getMemberId())) {
            throw new BusinessException("PAYMENT_ORDER_FORBIDDEN", "无权查看该支付单");
        }
        return PaymentOrderResponse.from(paymentOrder);
    }

    /**
     * 关闭会员未完成支付单。
     *
     * @param memberId 当前会员ID
     * @param paymentNo 支付单号
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void close(Long memberId, String paymentNo) {
        PaymentOrder paymentOrder = requirePaymentForUpdate(paymentNo);
        if (memberId == null || !memberId.equals(paymentOrder.getMemberId())) {
            throw new BusinessException("PAYMENT_ORDER_FORBIDDEN", "无权关闭该支付单");
        }
        if (paymentOrder.getStatus() == STATUS_SUCCEEDED) {
            throw new BusinessException("PAYMENT_ORDER_PAID", "支付成功的支付单不能关闭");
        }
        if (paymentOrder.getStatus() == STATUS_CLOSED) {
            return;
        }
        paymentOrder.setStatus(STATUS_CLOSED);
        updatePayment(paymentOrder);
    }

    /**
     * 处理支付平台异步通知，并以支付单号实现幂等。
     *
     * @param request 支付通知请求
     * @return 更新后的支付单响应
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public PaymentOrderResponse notifyPayment(PaymentNotifyRequest request) {
        PaymentOrder paymentOrder = requirePaymentForUpdate(request.paymentNo());
        if (paymentOrder.getStatus() == STATUS_SUCCEEDED) {
            if (!request.transactionNo().equals(paymentOrder.getTransactionNo())) {
                throw new BusinessException("PAYMENT_TRANSACTION_CONFLICT", "支付单已绑定其他第三方交易号");
            }
            return PaymentOrderResponse.from(paymentOrder);
        }
        if (paymentOrder.getStatus() == STATUS_CLOSED || paymentOrder.getStatus() == STATUS_FAILED) {
            throw new BusinessException("PAYMENT_ORDER_CLOSED", "支付单已关闭或失败，不能确认支付");
        }
        paymentOrder.setStatus(STATUS_SUCCEEDED);
        paymentOrder.setTransactionNo(request.transactionNo().trim());
        paymentOrder.setNotifyPayload(request.rawPayload());
        paymentOrder.setPaidAt(LocalDateTime.now());
        updatePayment(paymentOrder);

        TradeOrder order = tradeOrderService.findById(paymentOrder.getOrderId());
        boolean firstPayment = Integer.valueOf(0).equals(order.getPaymentStatus());
        if (firstPayment) {
            tradeOrderService.markPaid(paymentOrder.getOrderId(), paymentOrder.getChannel(),
                    paymentOrder.getAmount(), paymentOrder.getPaidAt());
        }
        return PaymentOrderResponse.from(paymentOrder);
    }

    /**
     * 定时关闭已过期且未完成的支付单。
     *
     * @author Henfon
     * @date 2026-08-30
     */
    @Scheduled(fixedDelayString = "${shop.payment.expire-scan-ms:60000}",
            initialDelayString = "${shop.payment.expire-initial-delay-ms:20000}")
    @Transactional
    public void closeExpiredPayments() {
        // 批量限制避免支付平台异常时单次锁住过多支付单记录。
        List<PaymentOrder> expired = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .in(PaymentOrder::getStatus, List.of(STATUS_PENDING, STATUS_PROCESSING))
                .le(PaymentOrder::getExpireAt, LocalDateTime.now())
                .orderByAsc(PaymentOrder::getExpireAt)
                .last("LIMIT 100"));
        for (PaymentOrder paymentOrder : expired) {
            paymentOrder.setStatus(STATUS_CLOSED);
            updatePayment(paymentOrder);
        }
    }

    /**
     * 查询并锁定支付单。
     *
     * @param paymentNo 支付单号
     * @return 支付单实体
     * @author Henfon
     * @date 2026-08-30
     */
    private PaymentOrder requirePaymentForUpdate(String paymentNo) {
        if (!StringUtils.hasText(paymentNo)) {
            throw new BusinessException("PAYMENT_ORDER_NOT_FOUND", "支付单号不能为空");
        }
        PaymentOrder paymentOrder = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getPaymentNo, paymentNo.trim())
                .last("LIMIT 1 FOR UPDATE"));
        if (paymentOrder == null) {
            throw new BusinessException("PAYMENT_ORDER_NOT_FOUND", "支付单不存在");
        }
        return paymentOrder;
    }

    /**
     * 查询支付单。
     *
     * @param paymentNo 支付单号
     * @return 支付单实体
     * @author Henfon
     * @date 2026-08-30
     */
    private PaymentOrder requirePayment(String paymentNo) {
        if (!StringUtils.hasText(paymentNo)) {
            throw new BusinessException("PAYMENT_ORDER_NOT_FOUND", "支付单号不能为空");
        }
        PaymentOrder paymentOrder = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getPaymentNo, paymentNo.trim()));
        if (paymentOrder == null) {
            throw new BusinessException("PAYMENT_ORDER_NOT_FOUND", "支付单不存在");
        }
        return paymentOrder;
    }

    /**
     * 查询并校验会员订单。
     *
     * @param memberId 当前会员ID
     * @param orderId 订单ID
     * @return 会员订单
     * @author Henfon
     * @date 2026-08-30
     */
    private TradeOrder requireMemberOrder(Long memberId, Long orderId) {
        TradeOrder order = tradeOrderService.findById(orderId);
        if (order == null) {
            throw new BusinessException("TRADE_ORDER_NOT_FOUND", "订单不存在");
        }
        if (memberId == null || !memberId.equals(order.getMemberId())) {
            throw new BusinessException("PAYMENT_ORDER_FORBIDDEN", "无权操作该订单");
        }
        return order;
    }

    /**
     * 使用乐观锁更新支付单。
     *
     * @param paymentOrder 支付单实体
     * @author Henfon
     * @date 2026-08-30
     */
    private void updatePayment(PaymentOrder paymentOrder) {
        // version 字段防止并发回调覆盖支付单最终状态。
        if (paymentOrderMapper.updateById(paymentOrder) == 0) {
            throw new BusinessException("PAYMENT_ORDER_CONCURRENT_UPDATE", "支付单已被其他操作修改");
        }
    }
}
