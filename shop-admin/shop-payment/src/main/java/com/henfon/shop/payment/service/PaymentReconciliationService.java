package com.henfon.shop.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.payment.dto.PaymentReconciliationRecord;
import com.henfon.shop.payment.entity.PaymentOrder;
import com.henfon.shop.payment.entity.PaymentRefundOrder;
import com.henfon.shop.payment.mapper.PaymentOrderMapper;
import com.henfon.shop.payment.mapper.PaymentRefundOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 财务对账查询服务，基于支付单和退款单事实记录生成统一流水视图。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
public class PaymentReconciliationService {

    private static final int PAYMENT_PENDING = 0;
    private static final int PAYMENT_PROCESSING = 1;
    private static final int PAYMENT_SUCCEEDED = 2;
    private static final int REFUND_PENDING = 0;
    private static final int REFUND_PROCESSING = 1;
    private static final int REFUND_SUCCEEDED = 2;
    private static final int REFUND_FAILED = 3;

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentRefundOrderMapper refundOrderMapper;

    /**
     * 创建财务对账查询服务。
     *
     * @param paymentOrderMapper 支付单数据访问对象
     * @param refundOrderMapper 退款单数据访问对象
     * @author Henfon
     * @date 2026-09-01
     */
    public PaymentReconciliationService(PaymentOrderMapper paymentOrderMapper,
                                        PaymentRefundOrderMapper refundOrderMapper) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.refundOrderMapper = refundOrderMapper;
    }

    /**
     * 分页查询支付和退款对账流水。
     *
     * @param keyword 流水号、订单号或备注关键字
     * @param type 流水类型，可选 order_income/refund_payout
     * @param status 对账状态，可选 reconciled/pending_settle/discrepancy
     * @param current 当前页
     * @param size 页大小
     * @return 统一对账分页结果
     * @author Henfon
     * @date 2026-09-01
     */
    public IPage<PaymentReconciliationRecord> page(String keyword, String type, String status,
                                                    long current, long size) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        List<PaymentReconciliationRecord> records = new ArrayList<>();

        // 支付单成功或待处理状态都保留在对账视图中，便于财务追踪在途资金。
        LambdaQueryWrapper<PaymentOrder> paymentQuery = new LambdaQueryWrapper<PaymentOrder>()
                .in(PaymentOrder::getStatus, List.of(PAYMENT_PENDING, PAYMENT_PROCESSING, PAYMENT_SUCCEEDED))
                .orderByDesc(PaymentOrder::getPaidAt)
                .orderByDesc(PaymentOrder::getCreatedAt);
        for (PaymentOrder payment : paymentOrderMapper.selectList(paymentQuery)) {
            PaymentReconciliationRecord record = toPaymentRecord(payment);
            if (matches(record, normalizedKeyword, type, status)) {
                records.add(record);
            }
        }

        // 退款金额以负数呈现，和收款流水放在同一张表即可直接计算净结算额。
        LambdaQueryWrapper<PaymentRefundOrder> refundQuery = new LambdaQueryWrapper<PaymentRefundOrder>()
                .in(PaymentRefundOrder::getStatus, List.of(REFUND_PENDING, REFUND_PROCESSING, REFUND_SUCCEEDED, REFUND_FAILED))
                .orderByDesc(PaymentRefundOrder::getRefundedAt)
                .orderByDesc(PaymentRefundOrder::getRequestedAt);
        for (PaymentRefundOrder refund : refundOrderMapper.selectList(refundQuery)) {
            PaymentReconciliationRecord record = toRefundRecord(refund);
            if (matches(record, normalizedKeyword, type, status)) {
                records.add(record);
            }
        }

        records.sort(Comparator.comparing(this::sortTime, Comparator.nullsLast(Comparator.reverseOrder())));
        Page<PaymentReconciliationRecord> result = new Page<>(safeCurrent, safeSize, records.size());
        int from = (int) Math.min((safeCurrent - 1) * safeSize, records.size());
        int to = (int) Math.min((long) from + safeSize, records.size());
        result.setRecords(records.subList(from, to));
        return result;
    }

    /**
     * 将支付单映射为统一对账记录。
     *
     * @param payment 支付单实体
     * @return 对账记录
     * @author Henfon
     * @date 2026-09-01
     */
    private PaymentReconciliationRecord toPaymentRecord(PaymentOrder payment) {
        String status = payment.getStatus() != null && payment.getStatus() == PAYMENT_SUCCEEDED
                ? "reconciled" : "pending_settle";
        BigDecimal amount = safeAmount(payment.getAmount());
        String settledAt = formatTime(payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt());
        String transNo = StringUtils.hasText(payment.getTransactionNo())
                ? payment.getTransactionNo() : payment.getPaymentNo();
        return new PaymentReconciliationRecord(
                "pay-" + payment.getId(), transNo, payment.getOrderNo(), "order_income",
                normalizeChannel(payment.getChannel()), amount, BigDecimal.ZERO, amount, status,
                settledAt, payment.getChannel(), "支付单 " + payment.getPaymentNo());
    }

    /**
     * 将退款单映射为统一对账记录。
     *
     * @param refund 退款单实体
     * @return 对账记录
     * @author Henfon
     * @date 2026-09-01
     */
    private PaymentReconciliationRecord toRefundRecord(PaymentRefundOrder refund) {
        String status;
        if (refund.getStatus() != null && refund.getStatus() == REFUND_SUCCEEDED) {
            status = "reconciled";
        } else if (refund.getStatus() != null
                && (refund.getStatus() == REFUND_PENDING || refund.getStatus() == REFUND_PROCESSING)) {
            status = "pending_settle";
        } else {
            status = "discrepancy";
        }
        BigDecimal amount = safeAmount(refund.getAmount()).negate();
        String settledAt = formatTime(refund.getRefundedAt() != null ? refund.getRefundedAt() : refund.getRequestedAt());
        String transNo = StringUtils.hasText(refund.getTransactionNo())
                ? refund.getTransactionNo() : refund.getRefundNo();
        return new PaymentReconciliationRecord(
                "refund-" + refund.getId(), transNo, refund.getOrderNo(), "refund_payout",
                "wechat_pay", amount, BigDecimal.ZERO, amount, status, settledAt,
                "退款原路渠道", StringUtils.hasText(refund.getReason()) ? refund.getReason() : "退款单 " + refund.getRefundNo());
    }

    /**
     * 判断记录是否命中查询条件。
     *
     * @param record 对账记录
     * @param keyword 归一化关键字
     * @param type 类型
     * @param status 状态
     * @return 是否命中
     * @author Henfon
     * @date 2026-09-01
     */
    private boolean matches(PaymentReconciliationRecord record, String keyword, String type, String status) {
        if (StringUtils.hasText(type) && !"all".equalsIgnoreCase(type) && !type.equals(record.type())) {
            return false;
        }
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status) && !status.equals(record.status())) {
            return false;
        }
        if (keyword == null) {
            return true;
        }
        String haystack = String.join(" ", safe(record.transNo()), safe(record.orderNumber()), safe(record.notes()))
                .toLowerCase(Locale.ROOT);
        return haystack.contains(keyword);
    }

    /**
     * 获取记录排序时间。
     *
     * @param record 对账记录
     * @return 可排序时间
     * @author Henfon
     * @date 2026-09-01
     */
    private LocalDateTime sortTime(PaymentReconciliationRecord record) {
        try {
            return record.settledAt() == null ? null : LocalDateTime.parse(record.settledAt());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 格式化时间供前端直接展示。
     *
     * @param time 时间
     * @return ISO 时间文本
     * @author Henfon
     * @date 2026-09-01
     */
    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.toString();
    }

    /**
     * 归一化支付渠道名称。
     *
     * @param channel 渠道编码
     * @return 前端渠道编码
     * @author Henfon
     * @date 2026-09-01
     */
    private String normalizeChannel(String channel) {
        if (!StringUtils.hasText(channel)) {
            return "balance_pay";
        }
        String normalized = channel.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "wechat", "wechat_pay", "wx" -> "wechat_pay";
            case "alipay", "ali_pay" -> "alipay";
            case "unionpay", "union_pay" -> "unionpay";
            default -> "balance_pay";
        };
    }

    /**
     * 返回非空金额。
     *
     * @param amount 金额
     * @return 安全金额
     * @author Henfon
     * @date 2026-09-01
     */
    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /**
     * 返回非空文本。
     *
     * @param value 文本
     * @return 安全文本
     * @author Henfon
     * @date 2026-09-01
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }
}
