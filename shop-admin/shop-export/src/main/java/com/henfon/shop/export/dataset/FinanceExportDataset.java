package com.henfon.shop.export.dataset;

import com.henfon.shop.export.dto.ExportQuery;
import com.henfon.shop.export.entity.ExportType;
import com.henfon.shop.export.excel.ExcelColumn;
import com.henfon.shop.payment.dto.PaymentReconciliationRecord;
import com.henfon.shop.payment.service.PaymentReconciliationService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 财务资金对账导出数据集。
 *
 * <p>对账流水由支付单与退款单聚合而成，直接复用财务模块的查询口径，
 * 不在导出侧重新实现一遍筛选逻辑，避免报表与页面出现对不上的数字。</p>
 *
 * @author Henfon
 * @date 2026-09-16
 */
@Component
public class FinanceExportDataset extends AbstractExportDataset<FinanceExportDataset.Row> {

    private static final Map<String, String> TYPE_LABELS = Map.of(
            "order_income", "订单收款", "refund_payout", "退款支出");

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "reconciled", "已对平", "pending_settle", "待结算", "discrepancy", "存在差异");

    private final PaymentReconciliationService paymentReconciliationService;

    /**
     * 创建财务导出数据集。
     *
     * @param paymentReconciliationService 财务对账查询服务
     * @author Henfon
     * @date 2026-09-16
     */
    public FinanceExportDataset(PaymentReconciliationService paymentReconciliationService) {
        this.paymentReconciliationService = paymentReconciliationService;
    }

    /**
     * 财务导出行。
     *
     * @param transNo 流水单号
     * @param orderNumber 关联业务单号
     * @param typeLabel 交易类型
     * @param channel 支付渠道
     * @param amount 交易金额
     * @param fee 手续费
     * @param netAmount 净结算额
     * @param statusLabel 对账状态
     * @param settledAt 结算时间
     * @param accountAndNotes 商户账号与说明
     * @author Henfon
     * @date 2026-09-16
     */
    public record Row(String transNo, String orderNumber, String typeLabel, String channel,
                      BigDecimal amount, BigDecimal fee, BigDecimal netAmount, String statusLabel,
                      String settledAt, String accountAndNotes) {
    }

    @Override
    public ExportType type() {
        return ExportType.FINANCE;
    }

    @Override
    public List<ExcelColumn<Row>> columns() {
        return List.of(
                ExcelColumn.text("流水单号", Row::transNo, 24),
                ExcelColumn.text("关联业务单号", Row::orderNumber, 22),
                ExcelColumn.text("交易类型", Row::typeLabel, 12),
                ExcelColumn.text("支付渠道", Row::channel, 14),
                ExcelColumn.money("交易金额(¥)", Row::amount, 14),
                ExcelColumn.money("手续费(¥)", Row::fee, 12),
                ExcelColumn.money("净结算额(¥)", Row::netAmount, 14),
                ExcelColumn.text("对账状态", Row::statusLabel, 12),
                ExcelColumn.text("结算时间", Row::settledAt, 20),
                ExcelColumn.text("商户账号/说明", Row::accountAndNotes, 34));
    }

    @Override
    public void stream(Consumer<Row> consumer, ExportQuery query) {
        // 财务服务按结算时间倒序流式推送，这里只做行映射，不在导出侧缓存整份对账流水。
        paymentReconciliationService.forEachExportRecord(query.keyword(), query.financeType(),
                query.statusText(), record -> consumer.accept(toRow(record)));
    }

    /**
     * 记录转导出行为。
     *
     * @param record 对账记录
     * @return 导出行
     * @author Henfon
     * @date 2026-09-16
     */
    private static Row toRow(PaymentReconciliationRecord record) {
        String accountAndNotes = StringUtils.hasText(record.notes())
                ? record.accountNumber() + " - " + record.notes() : record.accountNumber();
        return new Row(record.transNo(), record.orderNumber(),
                TYPE_LABELS.getOrDefault(record.type(), record.type()), record.channel(),
                record.amount(), record.fee(), record.netAmount(),
                STATUS_LABELS.getOrDefault(record.status(), record.status()),
                record.settledAt(), accountAndNotes);
    }
}
