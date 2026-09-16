package com.henfon.shop.export.dataset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.export.dto.ExportQuery;
import com.henfon.shop.export.entity.ExportType;
import com.henfon.shop.export.excel.ExcelColumn;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.mapper.TradeOrderMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 订单明细导出数据集。
 *
 * @author Henfon
 * @date 2026-09-16
 */
@Component
public class OrderExportDataset extends AbstractExportDataset<OrderExportDataset.Row> {

    /** 订单状态文案，与前端 backendOrderStatusToFrontend 的映射保持一致。 */
    private static final Map<Integer, String> STATUS_LABELS = Map.of(
            10, "待付款", 20, "待发货", 30, "已发货", 40, "已完成",
            50, "已取消", 60, "退款中", 70, "已退款");

    /** 订单标旗文案，与前端列表展示保持一致。 */
    private static final Map<String, String> FLAG_LABELS = Map.of(
            "red", "红旗-催发货/加急", "yellow", "黄旗-需核实改地址", "green", "绿旗-VIP客户/赠品",
            "blue", "蓝旗-普通备注", "purple", "紫旗-特殊跟进");

    private final TradeOrderMapper tradeOrderMapper;

    /**
     * 创建订单导出数据集。
     *
     * @param tradeOrderMapper 订单数据访问对象
     * @author Henfon
     * @date 2026-09-16
     */
    public OrderExportDataset(TradeOrderMapper tradeOrderMapper) {
        this.tradeOrderMapper = tradeOrderMapper;
    }

    /**
     * 订单导出行。
     *
     * @param orderNo 订单号
     * @param createdAt 下单时间
     * @param customerName 客户姓名
     * @param customerPhone 联系电话
     * @param paymentMethod 支付方式
     * @param paidAmount 实付金额
     * @param discountAmount 优惠减免
     * @param flag 标旗
     * @param statusLabel 订单状态
     * @param carrier 承运商
     * @param trackingNo 运单号
     * @param sellerRemark 卖家备注
     * @param shippingAddress 收货地址
     * @author Henfon
     * @date 2026-09-16
     */
    public record Row(String orderNo, LocalDateTime createdAt, String customerName, String customerPhone,
                      String paymentMethod, BigDecimal paidAmount, BigDecimal discountAmount, String flag,
                      String statusLabel, String carrier, String trackingNo, String sellerRemark,
                      String shippingAddress) {
    }

    @Override
    public ExportType type() {
        return ExportType.ORDER;
    }

    @Override
    public List<ExcelColumn<Row>> columns() {
        return List.of(
                ExcelColumn.text("订单号", Row::orderNo, 24),
                ExcelColumn.dateTime("下单时间", Row::createdAt, 21),
                ExcelColumn.text("客户姓名", Row::customerName, 14),
                ExcelColumn.text("联系电话", Row::customerPhone, 15),
                ExcelColumn.text("支付方式", Row::paymentMethod, 12),
                ExcelColumn.money("实付金额(¥)", Row::paidAmount, 14),
                ExcelColumn.money("优惠减免(¥)", Row::discountAmount, 14),
                ExcelColumn.text("标旗", Row::flag, 20),
                ExcelColumn.text("订单状态", Row::statusLabel, 12),
                ExcelColumn.text("承运商", Row::carrier, 16),
                ExcelColumn.text("运单号", Row::trackingNo, 22),
                ExcelColumn.text("卖家备注", Row::sellerRemark, 28),
                ExcelColumn.text("收货地址", Row::shippingAddress, 46));
    }

    @Override
    public void stream(Consumer<Row> consumer, ExportQuery query) {
        LambdaQueryWrapper<TradeOrder> wrapper = new LambdaQueryWrapper<>();
        Integer status = query.orderStatus() != null ? query.orderStatus() : query.status();
        wrapper.eq(status != null, TradeOrder::getOrderStatus, status);
        if (StringUtils.hasText(query.keyword())) {
            String keyword = query.keyword().trim();
            wrapper.and(condition -> condition.like(TradeOrder::getOrderNo, keyword)
                    .or().like(TradeOrder::getMemberName, keyword)
                    .or().like(TradeOrder::getReceiverName, keyword)
                    .or().like(TradeOrder::getReceiverPhone, keyword));
        }
        wrapper.orderByDesc(TradeOrder::getId);

        streamPages(consumer, (pageNo, pageSize) -> {
            Page<TradeOrder> page = tradeOrderMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
            return page.getRecords().stream().map(OrderExportDataset::toRow).toList();
        });
    }

    /**
     * 实体转导出行为。
     *
     * @param order 订单实体
     * @return 导出行
     * @author Henfon
     * @date 2026-09-16
     */
    private static Row toRow(TradeOrder order) {
        String customerName = StringUtils.hasText(order.getMemberName())
                ? order.getMemberName() : order.getReceiverName();
        return new Row(order.getOrderNo(), order.getCreatedAt(), customerName, order.getReceiverPhone(),
                order.getPaymentMethod(), order.getPaidAmount(), order.getDiscountAmount(),
                order.getFlagColor() == null ? null : FLAG_LABELS.getOrDefault(order.getFlagColor(), order.getFlagColor()),
                STATUS_LABELS.getOrDefault(order.getOrderStatus(), "未知状态"),
                order.getLogisticsCompany(), order.getTrackingNo(), order.getSellerRemark(),
                buildAddress(order));
    }

    /**
     * 拼接完整收货地址。
     *
     * @param order 订单实体
     * @return 收货地址
     * @author Henfon
     * @date 2026-09-16
     */
    private static String buildAddress(TradeOrder order) {
        StringBuilder address = new StringBuilder();
        appendPart(address, order.getReceiverProvince());
        appendPart(address, order.getReceiverCity());
        appendPart(address, order.getReceiverDistrict());
        appendPart(address, order.getReceiverAddress());
        return address.length() == 0 ? null : address.toString();
    }

    /**
     * 追加地址片段。
     *
     * @param builder 地址构造器
     * @param part 地址片段
     * @author Henfon
     * @date 2026-09-16
     */
    private static void appendPart(StringBuilder builder, String part) {
        if (StringUtils.hasText(part)) {
            builder.append(part.trim());
        }
    }
}
