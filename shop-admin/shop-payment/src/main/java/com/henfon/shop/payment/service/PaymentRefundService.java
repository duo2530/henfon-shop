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
import com.henfon.shop.trade.service.TradeAfterSaleService;
import com.henfon.shop.trade.service.TradeOrderService;
import com.henfon.shop.trade.service.TradeOrderStateMachine;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

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
    private final TradeAfterSaleService tradeAfterSaleService;

    /**
     * 创建退款应用服务。
     *
     * @param paymentOrderMapper 支付单数据访问对象
     * @param refundOrderMapper 退款单数据访问对象
     * @param tradeOrderService 交易订单应用服务
     * @author Henfon
     * @date 2026-08-30
     */
    @Autowired
    public PaymentRefundService(PaymentOrderMapper paymentOrderMapper, PaymentRefundOrderMapper refundOrderMapper,
                                TradeOrderService tradeOrderService, TradeAfterSaleService tradeAfterSaleService) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.refundOrderMapper = refundOrderMapper;
        this.tradeOrderService = tradeOrderService;
        this.tradeAfterSaleService = tradeAfterSaleService;
    }

    /**
     * 创建退款应用服务（兼容无售后联动调用方）。
     *
     * @param paymentOrderMapper 支付单数据访问对象
     * @param refundOrderMapper 退款单数据访问对象
     * @param tradeOrderService 交易订单应用服务
     * @author Henfon
     * @date 2026-08-31
     */
    public PaymentRefundService(PaymentOrderMapper paymentOrderMapper, PaymentRefundOrderMapper refundOrderMapper,
                                TradeOrderService tradeOrderService) {
        this(paymentOrderMapper, refundOrderMapper, tradeOrderService, null);
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
        validateCreateRequest(request);
        String idempotencyKey = normalize(request.idempotencyKey());
        if (idempotencyKey != null) {
            PaymentRefundOrder existing = refundOrderMapper.selectOne(new LambdaQueryWrapper<PaymentRefundOrder>()
                    .eq(PaymentRefundOrder::getIdempotencyKey, idempotencyKey).last("LIMIT 1 FOR UPDATE"));
            if (existing != null) {
                // 同一幂等键只能复用完全相同的退款请求，避免误把其他订单的退款单返回给调用方。
                if (!request.orderId().equals(existing.getOrderId())
                        || existing.getAmount() == null
                        || existing.getAmount().compareTo(request.amount()) != 0
                        || !Objects.equals(normalize(existing.getReason()), normalize(request.reason()))) {
                    throw new BusinessException("PAYMENT_REFUND_IDEMPOTENCY_CONFLICT", "退款幂等键已被其他请求使用");
                }
                return PaymentRefundResponse.from(existing);
            }
        }
        TradeOrder order = tradeOrderService.findById(request.orderId());
        if (Integer.valueOf(TradeOrderStateMachine.STATUS_REFUNDED).equals(order.getOrderStatus())
                || Integer.valueOf(2).equals(order.getPaymentStatus())) {
            throw new BusinessException("PAYMENT_REFUND_ALREADY_COMPLETED", "订单已完成退款，不能重复申请");
        }
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
        validateNotifyRequest(request);
        PaymentRefundOrder refundOrder = refundOrderMapper.selectOne(new LambdaQueryWrapper<PaymentRefundOrder>()
                .eq(PaymentRefundOrder::getRefundNo, request.refundNo().trim())
                .last("LIMIT 1 FOR UPDATE"));
        if (refundOrder == null) {
            throw new BusinessException("PAYMENT_REFUND_NOT_FOUND", "退款单不存在");
        }
        if (refundOrder.getStatus() == REFUND_SUCCEEDED || refundOrder.getStatus() == REFUND_FAILED) {
            if (StringUtils.hasText(refundOrder.getTransactionNo())
                    && !refundOrder.getTransactionNo().equals(request.transactionNo().trim())) {
                throw new BusinessException("PAYMENT_REFUND_NOTIFY_CONFLICT", "退款单已处理但第三方交易号不一致");
            }
            return PaymentRefundResponse.from(refundOrder);
        }
        if (refundOrder.getStatus() != REFUND_PENDING && refundOrder.getStatus() != REFUND_PROCESSING) {
            throw new BusinessException("PAYMENT_REFUND_STATUS_INVALID", "当前退款单状态不允许处理回调");
        }
        refundOrder.setTransactionNo(request.transactionNo().trim());
        refundOrder.setNotifyPayload(request.rawPayload());
        if (request.success()) {
            refundOrder.setStatus(REFUND_SUCCEEDED);
            refundOrder.setRefundedAt(LocalDateTime.now());
        } else {
            refundOrder.setStatus(REFUND_FAILED);
            refundOrder.setRemark("支付渠道退款失败");
        }
        if (refundOrderMapper.updateById(refundOrder) == 0) {
            throw new BusinessException("PAYMENT_REFUND_CONCURRENT_UPDATE", "退款单已被其他操作修改");
        }
        if (request.success()) {
            // 只有累计退款金额达到支付金额后，订单才从退款中推进到已退款。
            PaymentOrder paymentOrder = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                    .eq(PaymentOrder::getPaymentNo, refundOrder.getPaymentNo())
                    .last("LIMIT 1 FOR UPDATE"));
            BigDecimal totalRefunded = refundOrderMapper.selectList(new LambdaQueryWrapper<PaymentRefundOrder>()
                            .eq(PaymentRefundOrder::getPaymentNo, refundOrder.getPaymentNo())
                            .eq(PaymentRefundOrder::getStatus, REFUND_SUCCEEDED))
                    .stream()
                    .map(item -> item.getAmount() == null ? BigDecimal.ZERO : item.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (paymentOrder != null && paymentOrder.getAmount() != null
                    && totalRefunded.compareTo(paymentOrder.getAmount()) >= 0) {
                tradeOrderService.markRefunded(refundOrder.getOrderId(), totalRefunded);
            }
            // 即使订单尚未全额退款，对应仅退款售后单也应在本次资金成功后完成。
            if (tradeAfterSaleService != null) {
                tradeAfterSaleService.markRefundSucceeded(refundOrder.getOrderId(), refundOrder.getAmount());
            }
        } else {
            // 失败回调释放处理中售后目标，允许会员重新提交售后申请。
            if (tradeAfterSaleService != null) {
                tradeAfterSaleService.markRefundFailed(refundOrder.getOrderId(), refundOrder.getAmount());
            }
        }
        return PaymentRefundResponse.from(refundOrder);
    }

    /**
     * 校验退款创建请求，防止绕过控制器校验直接调用服务时产生非法退款单。
     *
     * @param request 退款创建请求
     * @author Henfon
     * @date 2026-08-31
     */
    private void validateCreateRequest(PaymentRefundCreateRequest request) {
        if (request == null || request.orderId() == null || request.amount() == null
                || request.amount().signum() <= 0) {
            throw new BusinessException("PAYMENT_REFUND_AMOUNT_INVALID", "退款订单和金额不能为空且金额必须大于0");
        }
    }

    /**
     * 校验退款回调请求，统一处理服务层直接调用的空值和空白参数。
     *
     * @param request 退款回调请求
     * @author Henfon
     * @date 2026-08-31
     */
    private void validateNotifyRequest(PaymentRefundNotifyRequest request) {
        if (request == null || !StringUtils.hasText(request.refundNo())
                || !StringUtils.hasText(request.transactionNo()) || request.success() == null) {
            throw new BusinessException("PAYMENT_REFUND_NOTIFY_INVALID", "退款回调参数不完整");
        }
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
