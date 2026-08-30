package com.henfon.shop.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.payment.dto.PaymentRefundCreateRequest;
import com.henfon.shop.payment.dto.PaymentRefundNotifyRequest;
import com.henfon.shop.payment.dto.PaymentRefundResponse;
import com.henfon.shop.payment.entity.PaymentOrder;
import com.henfon.shop.payment.entity.PaymentRefundOrder;
import com.henfon.shop.payment.mapper.PaymentOrderMapper;
import com.henfon.shop.payment.mapper.PaymentRefundOrderMapper;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.dto.TradeOrderRefundRequest;
import com.henfon.shop.trade.service.TradeOrderService;
import com.henfon.shop.trade.service.TradeOrderStateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款单应用服务。
 *
 * <p>当前实现负责退款单幂等、可退金额校验和异步结果落库，第三方原路退款调用由渠道适配器接入。</p>
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Service
public class PaymentRefundService {

    private static final int PAYMENT_SUCCEEDED = 2;
    private static final int REFUND_PENDING = 0;
    private static final int REFUND_PROCESSING = 1;
    private static final int REFUND_SUCCEEDED = 2;
    private static final int REFUND_FAILED = 3;
    private static final int REFUND_CLOSED = 4;

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentRefundOrderMapper refundOrderMapper;
    private final TradeOrderService tradeOrderService;

    /**
     * 创建退款应用服务。
     *
     * @param paymentOrderMapper 支付单数据访问对象
     * @param refundOrderMapper 退款单数据访问对象
     * @param tradeOrderService 交易订单应用服务
     * @author Henfon
     * @date 2026-08-30
     */
    public PaymentRefundService(PaymentOrderMapper paymentOrderMapper, PaymentRefundOrderMapper refundOrderMapper,
                                TradeOrderService tradeOrderService) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.refundOrderMapper = refundOrderMapper;
        this.tradeOrderService = tradeOrderService;
    }

    /**
     * 创建订单退款单。
     *
     * @param request 退款申请
     * @return 退款单响应
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public PaymentRefundResponse create(PaymentRefundCreateRequest request) {
        String idempotencyKey = normalize(request.idempotencyKey());
        if (idempotencyKey != null) {
            PaymentRefundOrder existing = refundOrderMapper.selectOne(new LambdaQueryWrapper<PaymentRefundOrder>()
                    .eq(PaymentRefundOrder::getIdempotencyKey, idempotencyKey).last("LIMIT 1 FOR UPDATE"));
            if (existing != null) {
                return PaymentRefundResponse.from(existing);
            }
        }
        TradeOrder order = tradeOrderService.findById(request.orderId());
        PaymentOrder paymentOrder = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderId, order.getId())
                .eq(PaymentOrder::getStatus, PAYMENT_SUCCEEDED)
                .orderByDesc(PaymentOrder::getPaidAt)
                .last("LIMIT 1 FOR UPDATE"));
        if (paymentOrder == null) {
            throw new BusinessException("PAYMENT_REFUND_PAYMENT_INVALID", "订单没有成功支付记录");
        }
        BigDecimal paidAmount = paymentOrder.getAmount() == null ? BigDecimal.ZERO : paymentOrder.getAmount();
        BigDecimal refundedAmount = refundOrderMapper.selectList(new LambdaQueryWrapper<PaymentRefundOrder>()
                        .eq(PaymentRefundOrder::getPaymentNo, paymentOrder.getPaymentNo())
                        .in(PaymentRefundOrder::getStatus, List.of(REFUND_PENDING, REFUND_PROCESSING, REFUND_SUCCEEDED)))
                .stream().map(item -> item.getAmount() == null ? BigDecimal.ZERO : item.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.amount().compareTo(paidAmount.subtract(refundedAmount)) > 0) {
            throw new BusinessException("PAYMENT_REFUND_AMOUNT_INVALID", "退款金额超过可退金额");
        }
        if (!Integer.valueOf(TradeOrderStateMachine.STATUS_REFUNDING).equals(order.getOrderStatus())) {
            // 创建退款单时同步推进订单到退款中，确保后台退款入口不会只留下资金侧记录。
            tradeOrderService.refund(order.getId(), new TradeOrderRefundRequest(request.amount(),
                    normalize(request.reason()) == null ? "后台创建退款单" : normalize(request.reason())));
        }
        PaymentRefundOrder refundOrder = new PaymentRefundOrder();
        refundOrder.setRefundNo("REF" + IdWorker.getIdStr());
        refundOrder.setIdempotencyKey(idempotencyKey);
        refundOrder.setPaymentNo(paymentOrder.getPaymentNo());
        refundOrder.setOrderId(order.getId());
        refundOrder.setOrderNo(order.getOrderNo());
        refundOrder.setMemberId(order.getMemberId());
        refundOrder.setAmount(request.amount());
        refundOrder.setReason(normalize(request.reason()));
        refundOrder.setStatus(REFUND_PENDING);
        refundOrder.setRequestedAt(LocalDateTime.now());
        refundOrderMapper.insert(refundOrder);
        return PaymentRefundResponse.from(refundOrder);
    }

    /**
     * 查询退款单。
     *
     * @param refundNo 退款单号
     * @return 退款单响应
     * @author Henfon
     * @date 2026-08-30
     */
    public PaymentRefundResponse get(String refundNo) {
        return PaymentRefundResponse.from(requireRefund(refundNo));
    }

    /**
     * 处理退款异步通知并保证重复通知幂等。
     *
     * @param request 退款通知请求
     * @return 更新后的退款单响应
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public PaymentRefundResponse notifyRefund(PaymentRefundNotifyRequest request) {
        PaymentRefundOrder refundOrder = refundOrderMapper.selectOne(new LambdaQueryWrapper<PaymentRefundOrder>()
                .eq(PaymentRefundOrder::getRefundNo, request.refundNo().trim())
                .last("LIMIT 1 FOR UPDATE"));
        if (refundOrder == null) {
            throw new BusinessException("PAYMENT_REFUND_NOT_FOUND", "退款单不存在");
        }
        if (refundOrder.getStatus() == REFUND_SUCCEEDED || refundOrder.getStatus() == REFUND_FAILED) {
            return PaymentRefundResponse.from(refundOrder);
        }
        refundOrder.setTransactionNo(request.transactionNo().trim());
        refundOrder.setNotifyPayload(request.rawPayload());
        if (request.success()) {
            refundOrder.setStatus(REFUND_SUCCEEDED);
            refundOrder.setRefundedAt(LocalDateTime.now());
        } else {
            refundOrder.setStatus(REFUND_FAILED);
        }
        if (refundOrderMapper.updateById(refundOrder) == 0) {
            throw new BusinessException("PAYMENT_REFUND_CONCURRENT_UPDATE", "退款单已被其他操作修改");
        }
        return PaymentRefundResponse.from(refundOrder);
    }

    /**
     * 查询退款单实体。
     *
     * @param refundNo 退款单号
     * @return 退款单实体
     * @author Henfon
     * @date 2026-08-30
     */
    private PaymentRefundOrder requireRefund(String refundNo) {
        if (!StringUtils.hasText(refundNo)) {
            throw new BusinessException("PAYMENT_REFUND_NOT_FOUND", "退款单号不能为空");
        }
        PaymentRefundOrder refundOrder = refundOrderMapper.selectOne(new LambdaQueryWrapper<PaymentRefundOrder>()
                .eq(PaymentRefundOrder::getRefundNo, refundNo.trim()));
        if (refundOrder == null) {
            throw new BusinessException("PAYMENT_REFUND_NOT_FOUND", "退款单不存在");
        }
        return refundOrder;
    }

    /**
     * 规范化可选文本。
     *
     * @param value 原始文本
     * @return 去空白后的文本或空值
     * @author Henfon
     * @date 2026-08-30
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
