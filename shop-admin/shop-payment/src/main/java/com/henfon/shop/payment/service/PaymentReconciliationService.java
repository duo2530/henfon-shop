package com.henfon.shop.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.payment.dto.PaymentReconciliationRecord;
import com.henfon.shop.payment.dto.PaymentReconciliationActionRequest;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.payment.entity.PaymentOrder;
import com.henfon.shop.payment.entity.PaymentRefundOrder;
import com.henfon.shop.payment.mapper.PaymentOrderMapper;
import com.henfon.shop.payment.mapper.PaymentRefundOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        Map<String, PaymentOrder> paymentByNo = new HashMap<>();

        // 支付单成功或待处理状态都保留在对账视图中，便于财务追踪在途资金。
        LambdaQueryWrapper<PaymentOrder> paymentQuery = new LambdaQueryWrapper<PaymentOrder>()
                .in(PaymentOrder::getStatus, List.of(PAYMENT_PENDING, PAYMENT_PROCESSING, PAYMENT_SUCCEEDED))
                .orderByDesc(PaymentOrder::getPaidAt)
                .orderByDesc(PaymentOrder::getCreatedAt);
        for (PaymentOrder payment : paymentOrderMapper.selectList(paymentQuery)) {
            if (StringUtils.hasText(payment.getPaymentNo())) {
                // 使用支付单号建立索引，供退款流水做资金事实关联校验。
                paymentByNo.put(payment.getPaymentNo(), payment);
            }
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
        List<PaymentRefundOrder> refunds = refundOrderMapper.selectList(refundQuery);
        Map<String, BigDecimal> successfulRefundTotals = new HashMap<>();
        for (PaymentRefundOrder refund : refunds) {
            if (refund.getStatus() != null && refund.getStatus() == REFUND_SUCCEEDED
                    && StringUtils.hasText(refund.getPaymentNo())) {
                // 先汇总同一支付单的成功退款，校验多次部分退款是否超出实付金额。
                successfulRefundTotals.merge(refund.getPaymentNo(), safeAmount(refund.getAmount()), BigDecimal::add);
            }
        }
        Set<String> overRefundPaymentNos = new HashSet<>();
        successfulRefundTotals.forEach((paymentNo, total) -> {
            PaymentOrder payment = paymentByNo.get(paymentNo);
            if (payment != null && total.compareTo(safeAmount(payment.getAmount())) > 0) {
                overRefundPaymentNos.add(paymentNo);
            }
        });
        for (PaymentRefundOrder refund : refunds) {
            PaymentReconciliationRecord record = toRefundRecord(refund, paymentByNo, overRefundPaymentNos);
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
     * 处理对账差异记录，支持确认、忽略、备注和退款重新匹配。
     *
     * @param recordId 对账记录ID，格式为pay-主键或refund-主键
     * @param request 人工处理请求
     * @return 更新后的对账记录
     * @author Henfon
     * @date 2026-09-04
     */
    @Transactional
    public PaymentReconciliationRecord action(String recordId, PaymentReconciliationActionRequest request) {
        if (!StringUtils.hasText(recordId) || request == null || !StringUtils.hasText(request.action())) {
            throw new BusinessException("PAYMENT_RECONCILIATION_ACTION_INVALID", "对账处理参数不完整");
        }
        String[] parts = recordId.trim().split("-", 2);
        if (parts.length != 2) {
            throw new BusinessException("PAYMENT_RECONCILIATION_RECORD_NOT_FOUND", "对账记录不存在");
        }
        Long id;
        try {
            id = Long.valueOf(parts[1]);
        } catch (NumberFormatException ex) {
            throw new BusinessException("PAYMENT_RECONCILIATION_RECORD_NOT_FOUND", "对账记录不存在");
        }
        String action = request.action().trim().toLowerCase(Locale.ROOT);
        if ("pay".equals(parts[0])) {
            PaymentOrder payment = paymentOrderMapper.selectById(id);
            if (payment == null) {
                throw new BusinessException("PAYMENT_RECONCILIATION_RECORD_NOT_FOUND", "对账记录不存在");
            }
            applyAction(payment, action, request.remark());
            paymentOrderMapper.updateById(payment);
            return toPaymentRecord(payment);
        }
        if (!"refund".equals(parts[0])) {
            throw new BusinessException("PAYMENT_RECONCILIATION_RECORD_NOT_FOUND", "对账记录不存在");
        }
        PaymentRefundOrder refund = refundOrderMapper.selectById(id);
        if (refund == null) {
            throw new BusinessException("PAYMENT_RECONCILIATION_RECORD_NOT_FOUND", "对账记录不存在");
        }
        if ("rematch".equals(action)) {
            if (!StringUtils.hasText(request.matchPaymentNo())) {
                throw new BusinessException("PAYMENT_RECONCILIATION_MATCH_INVALID", "重新匹配必须填写支付单号");
            }
            PaymentOrder payment = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                    .eq(PaymentOrder::getPaymentNo, request.matchPaymentNo().trim()).last("LIMIT 1"));
            if (payment == null || !Integer.valueOf(PAYMENT_SUCCEEDED).equals(payment.getStatus())) {
                throw new BusinessException("PAYMENT_RECONCILIATION_MATCH_INVALID", "支付单不存在或未支付成功");
            }
            // 重新匹配后保留原退款金额及流水，仅修正资金事实关联关系。
            refund.setPaymentNo(payment.getPaymentNo());
            refund.setReconciliationStatus("reconciled");
        } else {
            applyAction(refund, action, request.remark());
        }
        if (StringUtils.hasText(request.remark())) {
            refund.setReconciliationRemark(request.remark().trim());
        }
        refundOrderMapper.updateById(refund);
        return toRefundRecord(refund, Map.of(), Set.of());
    }

    /**
     * 应用支付单人工对账状态。
     *
     * @param payment 支付单
     * @param action 处理动作
     * @param remark 处理备注
     * @author Henfon
     * @date 2026-09-04
     */
    private void applyAction(PaymentOrder payment, String action, String remark) {
        if ("confirm".equals(action) || "reconcile".equals(action)) {
            payment.setReconciliationStatus("reconciled");
        } else if ("ignore".equals(action)) {
            payment.setReconciliationStatus("ignored");
        } else if (!"remark".equals(action)) {
            throw new BusinessException("PAYMENT_RECONCILIATION_ACTION_INVALID", "不支持的对账处理动作");
        }
        if (StringUtils.hasText(remark)) payment.setReconciliationRemark(remark.trim());
    }

    /**
     * 应用退款单人工对账状态。
     *
     * @param refund 退款单
     * @param action 处理动作
     * @param remark 处理备注
     * @author Henfon
     * @date 2026-09-04
     */
    private void applyAction(PaymentRefundOrder refund, String action, String remark) {
        if ("confirm".equals(action) || "reconcile".equals(action)) {
            refund.setReconciliationStatus("reconciled");
        } else if ("ignore".equals(action)) {
            refund.setReconciliationStatus("ignored");
        } else if (!"remark".equals(action)) {
            throw new BusinessException("PAYMENT_RECONCILIATION_ACTION_INVALID", "不支持的对账处理动作");
        }
        if (StringUtils.hasText(remark)) refund.setReconciliationRemark(remark.trim());
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
        if (StringUtils.hasText(payment.getReconciliationStatus())) {
            status = payment.getReconciliationStatus();
        }
        BigDecimal amount = safeAmount(payment.getAmount());
        String settledAt = formatTime(payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt());
        String transNo = StringUtils.hasText(payment.getTransactionNo())
                ? payment.getTransactionNo() : payment.getPaymentNo();
        return new PaymentReconciliationRecord(
                "pay-" + payment.getId(), transNo, payment.getOrderNo(), "order_income",
                normalizeChannel(payment.getChannel()), amount, BigDecimal.ZERO, amount, status,
                settledAt, payment.getChannel(), StringUtils.hasText(payment.getReconciliationRemark())
                        ? payment.getReconciliationRemark() : "支付单 " + payment.getPaymentNo());
    }

    /**
     * 将退款单映射为统一对账记录。
     *
     * @param refund 退款单实体
     * @return 对账记录
     * @author Henfon
     * @date 2026-09-01
     */
    private PaymentReconciliationRecord toRefundRecord(PaymentRefundOrder refund,
                                                       Map<String, PaymentOrder> paymentByNo,
                                                       Set<String> overRefundPaymentNos) {
        String status;
        if (refund.getStatus() != null && refund.getStatus() == REFUND_SUCCEEDED) {
            PaymentOrder payment = StringUtils.hasText(refund.getPaymentNo())
                    ? paymentByNo.get(refund.getPaymentNo()) : null;
            // 退款成功必须能关联到成功支付单，否则属于渠道回调孤儿流水，需要人工核查。
            status = payment != null && PAYMENT_SUCCEEDED == payment.getStatus()
                    && !overRefundPaymentNos.contains(refund.getPaymentNo())
                    ? "reconciled" : "discrepancy";
        } else if (refund.getStatus() != null
                && (refund.getStatus() == REFUND_PENDING || refund.getStatus() == REFUND_PROCESSING)) {
            status = "pending_settle";
        } else {
            status = "discrepancy";
        }
        if (StringUtils.hasText(refund.getReconciliationStatus())) {
            status = refund.getReconciliationStatus();
        }
        BigDecimal amount = safeAmount(refund.getAmount()).negate();
        String settledAt = formatTime(refund.getRefundedAt() != null ? refund.getRefundedAt() : refund.getRequestedAt());
        String transNo = StringUtils.hasText(refund.getTransactionNo())
                ? refund.getTransactionNo() : refund.getRefundNo();
        return new PaymentReconciliationRecord(
                "refund-" + refund.getId(), transNo, refund.getOrderNo(), "refund_payout",
                "wechat_pay", amount, BigDecimal.ZERO, amount, status, settledAt,
                "退款原路渠道", StringUtils.hasText(refund.getReconciliationRemark())
                        ? refund.getReconciliationRemark()
                        : (StringUtils.hasText(refund.getReason()) ? refund.getReason() : "退款单 " + refund.getRefundNo()));
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
