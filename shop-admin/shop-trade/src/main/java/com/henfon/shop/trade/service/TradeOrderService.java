package com.henfon.shop.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.entity.TradeOrderItem;
import com.henfon.shop.trade.mapper.TradeOrderMapper;
import com.henfon.shop.trade.mapper.TradeOrderItemMapper;
import com.henfon.shop.trade.mapper.TradeOrderLogisticsMapper;
import com.henfon.shop.trade.entity.TradeOrderLogistics;
import com.henfon.shop.trade.dto.TradeOrderCreateRequest;
import com.henfon.shop.trade.dto.TradeOrderShipRequest;
import com.henfon.shop.trade.dto.TradeOrderCancelRequest;
import com.henfon.shop.trade.dto.TradeOrderRemarkRequest;
import com.henfon.shop.trade.dto.TradeOrderRefundRequest;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.inventory.dto.InventoryReservationItem;
import com.henfon.shop.inventory.service.InventoryStockService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 交易订单应用服务。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class TradeOrderService {

    private static final int STATUS_PENDING_PAYMENT = 10;
    private static final int STATUS_PENDING_SHIPMENT = 20;
    private static final int STATUS_SHIPPED = 30;
    private static final int STATUS_COMPLETED = 40;
    private static final int STATUS_CANCELLED = 50;
    private static final int STATUS_REFUNDING = 60;

    private final TradeOrderMapper tradeOrderMapper;
    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final TradeOrderLogisticsMapper tradeOrderLogisticsMapper;
    private final InventoryStockService inventoryStockService;

    /**
     * 创建交易订单服务。
     *
     * @param tradeOrderMapper 订单数据访问对象
     * @author Henfon
     * @date 2026-08-29
     */
    public TradeOrderService(TradeOrderMapper tradeOrderMapper, TradeOrderItemMapper tradeOrderItemMapper,
                             TradeOrderLogisticsMapper tradeOrderLogisticsMapper,
                             InventoryStockService inventoryStockService) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.tradeOrderItemMapper = tradeOrderItemMapper;
        this.tradeOrderLogisticsMapper = tradeOrderLogisticsMapper;
        this.inventoryStockService = inventoryStockService;
    }

    /**
     * 分页查询后台订单。
     *
     * @param keyword 订单号、会员或收货人关键字
     * @param orderStatus 订单状态
     * @param current 当前页
     * @param size 页大小
     * @return 订单分页结果
     * @author Henfon
     * @date 2026-08-29
     */
    public IPage<TradeOrder> page(String keyword, Integer orderStatus, long current, long size) {
        // 限制分页大小，避免后台查询一次性读取过多订单。
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        LambdaQueryWrapper<TradeOrder> wrapper = new LambdaQueryWrapper<TradeOrder>()
                .eq(orderStatus != null, TradeOrder::getOrderStatus, orderStatus)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(TradeOrder::getOrderNo, keyword)
                        .or().like(TradeOrder::getMemberName, keyword)
                        .or().like(TradeOrder::getReceiverName, keyword))
                .orderByDesc(TradeOrder::getCreatedAt);
        return tradeOrderMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
    }

    /**
     * 查询订单明细。
     *
     * @param orderId 订单ID
     * @return 订单明细列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<TradeOrderItem> listItems(Long orderId) {
        // 明细按创建顺序返回，保证后台展示与下单顺序一致。
        return tradeOrderItemMapper.selectList(new LambdaQueryWrapper<TradeOrderItem>()
                .eq(TradeOrderItem::getOrderId, orderId)
                .orderByAsc(TradeOrderItem::getId));
    }

    /**
     * 查询订单物流轨迹。
     *
     * @param orderId 订单ID
     * @return 按节点顺序排列的物流轨迹
     * @author Henfon
     * @date 2026-08-29
     */
    public List<TradeOrderLogistics> listLogistics(Long orderId) {
        // 轨迹按业务排序号和发生时间排序，兼容第三方物流回传乱序。
        return tradeOrderLogisticsMapper.selectList(new LambdaQueryWrapper<TradeOrderLogistics>()
                .eq(TradeOrderLogistics::getOrderId, orderId)
                .orderByAsc(TradeOrderLogistics::getSortNo)
                .orderByAsc(TradeOrderLogistics::getEventTime));
    }

    /**
     * 后台订单发货并记录首个物流节点。
     *
     * @param orderId 订单ID
     * @param request 发货请求
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public void ship(Long orderId, TradeOrderShipRequest request) {
        // 先读取订单并校验状态，避免已发货订单被重复覆盖物流信息。
        TradeOrder order = requireOrder(orderId);
        if (!Integer.valueOf(STATUS_PENDING_SHIPMENT).equals(order.getOrderStatus())) {
            throw new BusinessException("TRADE_ORDER_STATUS_INVALID", "仅待发货订单允许发货");
        }
        LocalDateTime now = LocalDateTime.now();
        order.setOrderStatus(STATUS_SHIPPED);
        order.setLogisticsCompany(request.logisticsCompany().trim());
        order.setTrackingNo(request.trackingNo().trim());
        order.setShippedAt(now);
        updateOrder(order);

        // 物流轨迹与订单状态在同一事务内写入，确保后台发货后门户可以立即查看节点。
        TradeOrderLogistics logistics = new TradeOrderLogistics();
        logistics.setOrderId(orderId);
        logistics.setTrackingNo(order.getTrackingNo());
        logistics.setLogisticsCompany(order.getLogisticsCompany());
        logistics.setLogisticsStatus("SHIPPED");
        logistics.setEventTime(now);
        logistics.setEventDescription("商家已发货，等待物流揽收");
        logistics.setSortNo(0);
        tradeOrderLogisticsMapper.insert(logistics);
    }

    /**
     * 后台取消订单。
     *
     * @param orderId 订单ID
     * @param request 取消请求
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public void cancel(Long orderId, TradeOrderCancelRequest request) {
        TradeOrder order = requireOrder(orderId);
        if (!Integer.valueOf(STATUS_PENDING_PAYMENT).equals(order.getOrderStatus())
                && !Integer.valueOf(STATUS_PENDING_SHIPMENT).equals(order.getOrderStatus())) {
            throw new BusinessException("TRADE_ORDER_STATUS_INVALID", "当前订单状态不允许取消");
        }
        order.setOrderStatus(STATUS_CANCELLED);
        if (StringUtils.hasText(request.reason())) {
            order.setRemark("后台取消：" + request.reason().trim());
        }
        updateOrder(order);
        // 取消待付款或待发货订单时释放已预占库存，库存与订单状态保持一致。
        inventoryStockService.release(order.getId(), order.getOrderNo());
    }

    /**
     * 更新订单卖家备注。
     *
     * @param orderId 订单ID
     * @param request 备注请求
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public void updateRemark(Long orderId, TradeOrderRemarkRequest request) {
        TradeOrder order = requireOrder(orderId);
        order.setSellerRemark(request.sellerRemark() == null ? null : request.sellerRemark().trim());
        updateOrder(order);
    }

    /**
     * 后台确认订单退款。
     *
     * <p>当前支付模块尚未接入第三方退款，先将订单置为退款中并记录原因；支付模块完成后由退款单和回调推进最终状态。</p>
     *
     * @param orderId 订单ID
     * @param request 退款请求
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public void refund(Long orderId, TradeOrderRefundRequest request) {
        TradeOrder order = requireOrder(orderId);
        if (!Integer.valueOf(1).equals(order.getPaymentStatus())) {
            throw new BusinessException("TRADE_ORDER_PAYMENT_INVALID", "只有已支付订单允许退款");
        }
        BigDecimal paidAmount = order.getPaidAmount() == null ? BigDecimal.ZERO : order.getPaidAmount();
        if (request.refundAmount().compareTo(paidAmount) > 0) {
            throw new BusinessException("TRADE_REFUND_AMOUNT_INVALID", "退款金额不能超过实付金额");
        }
        if (STATUS_CANCELLED == order.getOrderStatus() || STATUS_REFUNDING == order.getOrderStatus()) {
            throw new BusinessException("TRADE_ORDER_STATUS_INVALID", "当前订单状态不允许退款");
        }
        order.setOrderStatus(STATUS_REFUNDING);
        order.setPaymentStatus(2);
        order.setRemark("后台退款：" + request.reason().trim() + "，金额：" + request.refundAmount());
        updateOrder(order);
    }

    /**
     * 查询并校验订单存在。
     *
     * @param orderId 订单ID
     * @return 订单实体
     * @author Henfon
     * @date 2026-08-29
     */
    private TradeOrder requireOrder(Long orderId) {
        TradeOrder order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("TRADE_ORDER_NOT_FOUND", "订单不存在或已删除");
        }
        return order;
    }

    /**
     * 使用乐观锁更新订单。
     *
     * @param order 订单实体
     * @author Henfon
     * @date 2026-08-29
     */
    private void updateOrder(TradeOrder order) {
        // updateById 会携带 version 条件，防止并发操作覆盖最新订单状态。
        if (tradeOrderMapper.updateById(order) == 0) {
            throw new BusinessException("TRADE_ORDER_CONCURRENT_UPDATE", "订单已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 创建门户订单并写入订单明细。
     *
     * @param request 订单创建请求
     * @return 新订单
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public TradeOrder create(TradeOrderCreateRequest request) {
        TradeOrder order = new TradeOrder();
        order.setOrderNo("AO" + IdWorker.getIdStr());
        order.setMemberId(request.memberId());
        order.setOrderStatus(20);
        order.setPaymentStatus(1);
        order.setPaymentMethod(request.paymentMethod());
        order.setSubtotalAmount(request.subtotalAmount());
        order.setDiscountAmount(request.discountAmount());
        order.setFreightAmount(request.freightAmount());
        order.setPayableAmount(request.payableAmount());
        order.setPaidAmount(request.payableAmount());
        order.setReceiverName(request.receiverName());
        order.setReceiverPhone(request.receiverPhone());
        order.setReceiverProvince(request.receiverProvince());
        order.setReceiverCity(request.receiverCity());
        order.setReceiverDistrict(request.receiverDistrict());
        order.setReceiverAddress(request.receiverAddress());
        order.setPaidAt(java.time.LocalDateTime.now());
        tradeOrderMapper.insert(order);
        for (TradeOrderCreateRequest.Item itemRequest : request.items()) {
            TradeOrderItem item = new TradeOrderItem();
            item.setOrderId(order.getId());
            item.setProductId(itemRequest.productId());
            item.setSkuId(itemRequest.skuId());
            item.setProductName(itemRequest.productName());
            item.setSkuName(itemRequest.skuName());
            item.setSkuCode(itemRequest.skuCode());
            item.setImageUrl(itemRequest.imageUrl());
            item.setUnitPrice(itemRequest.unitPrice());
            item.setQuantity(itemRequest.quantity());
            item.setItemAmount(itemRequest.unitPrice().multiply(BigDecimal.valueOf(itemRequest.quantity())));
            tradeOrderItemMapper.insert(item);
        }
        // 订单和库存预占处于同一事务，库存不足时整个订单创建会回滚。
        inventoryStockService.reserve(order.getId(), order.getOrderNo(), request.items().stream()
                .map(item -> new InventoryReservationItem(item.productId(), item.skuId(), item.quantity()))
                .toList());
        return order;
    }
}
