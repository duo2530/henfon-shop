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
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.entity.CatalogSku;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.catalog.mapper.CatalogSkuMapper;
import com.henfon.shop.integration.messaging.RocketMqTopics;
import com.henfon.shop.identity.service.MemberAdminService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private final TradeOrderMapper tradeOrderMapper;
    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final TradeOrderLogisticsMapper tradeOrderLogisticsMapper;
    private final InventoryStockService inventoryStockService;
    private final TradeEventOutboxService tradeEventOutboxService;
    private final CatalogProductMapper catalogProductMapper;
    private final CatalogSkuMapper catalogSkuMapper;
    private final MemberAdminService memberAdminService;

    /**
     * 创建交易订单服务。
     *
     * @param tradeOrderMapper 订单数据访问对象
     * @author Henfon
     * @date 2026-08-29
     */
    public TradeOrderService(TradeOrderMapper tradeOrderMapper, TradeOrderItemMapper tradeOrderItemMapper,
                             TradeOrderLogisticsMapper tradeOrderLogisticsMapper,
                             InventoryStockService inventoryStockService,
                             TradeEventOutboxService tradeEventOutboxService,
                             CatalogProductMapper catalogProductMapper,
                             CatalogSkuMapper catalogSkuMapper,
                             MemberAdminService memberAdminService) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.tradeOrderItemMapper = tradeOrderItemMapper;
        this.tradeOrderLogisticsMapper = tradeOrderLogisticsMapper;
        this.inventoryStockService = inventoryStockService;
        this.tradeEventOutboxService = tradeEventOutboxService;
        this.catalogProductMapper = catalogProductMapper;
        this.catalogSkuMapper = catalogSkuMapper;
        this.memberAdminService = memberAdminService;
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
        TradeOrderStateMachine.requireTransition(order.getOrderStatus(), TradeOrderStateMachine.STATUS_SHIPPED);
        LocalDateTime now = LocalDateTime.now();
        order.setOrderStatus(TradeOrderStateMachine.STATUS_SHIPPED);
        order.setLogisticsCompany(request.logisticsCompany().trim());
        order.setTrackingNo(request.trackingNo().trim());
        order.setShippedAt(now);
        updateOrder(order);
        // 发货成功同时扣减锁定库存，任一环节失败都会回滚订单状态和库存变更。
        inventoryStockService.deduct(order.getId(), order.getOrderNo());

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
        tradeEventOutboxService.recordOrderShipped(order);
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
        TradeOrderStateMachine.requireTransition(order.getOrderStatus(), TradeOrderStateMachine.STATUS_CANCELLED);
        order.setOrderStatus(TradeOrderStateMachine.STATUS_CANCELLED);
        if (StringUtils.hasText(request.reason())) {
            order.setRemark("后台取消：" + request.reason().trim());
        }
        updateOrder(order);
        // 取消待付款或待发货订单时释放已预占库存，库存与订单状态保持一致。
        inventoryStockService.release(order.getId(), order.getOrderNo());
        tradeEventOutboxService.recordOrderCancelled(order, false);
    }

    /**
     * 门户会员取消自己的订单。
     *
     * @param memberId 会员ID
     * @param orderId 订单ID
     * @param reason 取消原因
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void cancelByMember(Long memberId, Long orderId, String reason) {
        TradeOrder order = requireMemberOrder(memberId, orderId);
        TradeOrderStateMachine.requireTransition(order.getOrderStatus(), TradeOrderStateMachine.STATUS_CANCELLED);
        order.setOrderStatus(TradeOrderStateMachine.STATUS_CANCELLED);
        if (StringUtils.hasText(reason)) {
            order.setRemark("买家取消：" + reason.trim());
        }
        updateOrder(order);
        // 买家取消同样释放订单创建时预占的库存。
        inventoryStockService.release(order.getId(), order.getOrderNo());
        tradeEventOutboxService.recordOrderCancelled(order, false);
    }

    /**
     * 门户会员确认收货。
     *
     * @param memberId 会员ID
     * @param orderId 订单ID
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void confirmReceive(Long memberId, Long orderId) {
        TradeOrder order = requireMemberOrder(memberId, orderId);
        TradeOrderStateMachine.requireTransition(order.getOrderStatus(), TradeOrderStateMachine.STATUS_COMPLETED);
        order.setOrderStatus(TradeOrderStateMachine.STATUS_COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        updateOrder(order);
        tradeEventOutboxService.recordOrderCompleted(order);
    }

    /**
     * 查询订单应用服务。
     *
     * @param orderId 订单ID
     * @return 订单实体
     * @author Henfon
     * @date 2026-08-30
     */
    public TradeOrder findById(Long orderId) {
        // 支付等领域服务通过应用服务读取订单，避免跨模块直接访问交易 Mapper。
        return requireOrder(orderId);
    }

    /**
     * 将待付款订单标记为已支付并记录领域事件。
     *
     * @param orderId 订单ID
     * @param paymentMethod 支付渠道
     * @param paidAmount 实付金额
     * @param paidAt 支付时间
     * @return 更新后的订单
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public TradeOrder markPaid(Long orderId, String paymentMethod, BigDecimal paidAmount, LocalDateTime paidAt) {
        TradeOrder order = requireOrder(orderId);
        if (Integer.valueOf(1).equals(order.getPaymentStatus())) {
            return order;
        }
        if (!Integer.valueOf(0).equals(order.getPaymentStatus())
                || !Integer.valueOf(TradeOrderStateMachine.STATUS_PENDING_PAYMENT).equals(order.getOrderStatus())) {
            throw new BusinessException("PAYMENT_TRADE_STATUS_INVALID", "订单当前状态不允许确认支付");
        }
        TradeOrderStateMachine.requireTransition(order.getOrderStatus(),
                TradeOrderStateMachine.STATUS_PENDING_SHIPMENT);
        order.setOrderStatus(TradeOrderStateMachine.STATUS_PENDING_SHIPMENT);
        order.setPaymentStatus(1);
        order.setPaymentMethod(paymentMethod);
        order.setPaidAmount(paidAmount);
        order.setPaidAt(paidAt);
        updateOrder(order);
        // 支付成功后同步会员消费统计，退款成功时再按原支付金额回滚。
        memberAdminService.recordPaid(order.getMemberId(), paidAmount, paidAt);
        // 订单状态与支付成功事件处于同一事务，保证状态变更后事件可被可靠投递。
        tradeEventOutboxService.recordOrderEvent(order, "PAYMENT_SUCCEEDED", RocketMqTopics.PAYMENT_SUCCEEDED);
        return order;
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
        TradeOrderStateMachine.requireTransition(order.getOrderStatus(), TradeOrderStateMachine.STATUS_REFUNDING);
        order.setOrderStatus(TradeOrderStateMachine.STATUS_REFUNDING);
        order.setRemark("后台退款：" + request.reason().trim() + "，金额：" + request.refundAmount());
        updateOrder(order);
        tradeEventOutboxService.recordOrderEvent(order, "REFUND_APPROVED", RocketMqTopics.REFUND_APPROVED);
    }

    /**
     * 退款回调成功后将订单标记为已退款。
     *
     * @param orderId 订单ID
     * @param refundAmount 退款金额
     * @return 更新后的订单
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public TradeOrder markRefunded(Long orderId, BigDecimal refundAmount) {
        TradeOrder order = requireOrder(orderId);
        if (Integer.valueOf(2).equals(order.getPaymentStatus())
                && Integer.valueOf(TradeOrderStateMachine.STATUS_REFUNDED).equals(order.getOrderStatus())) {
            return order;
        }
        TradeOrderStateMachine.requireTransition(order.getOrderStatus(), TradeOrderStateMachine.STATUS_REFUNDED);
        order.setOrderStatus(TradeOrderStateMachine.STATUS_REFUNDED);
        order.setPaymentStatus(2);
        order.setRemark("退款成功，金额：" + refundAmount);
        updateOrder(order);
        // 统计按有效消费口径回滚退款金额，全额退款同时减少订单数。
        memberAdminService.recordRefunded(order.getMemberId(), refundAmount, order.getPaidAmount());
        tradeEventOutboxService.recordOrderEvent(order, "REFUND_SUCCEEDED", RocketMqTopics.REFUND_SUCCEEDED);
        return order;
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
     * 查询并校验订单属于指定会员。
     *
     * @param memberId 会员ID
     * @param orderId 订单ID
     * @return 会员订单
     * @author Henfon
     * @date 2026-08-30
     */
    private TradeOrder requireMemberOrder(Long memberId, Long orderId) {
        TradeOrder order = requireOrder(orderId);
        if (memberId == null || !memberId.equals(order.getMemberId())) {
            throw new BusinessException("TRADE_ORDER_FORBIDDEN", "无权操作该订单");
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
        String idempotencyKey = StringUtils.hasText(request.idempotencyKey())
                ? request.idempotencyKey().trim() : null;
        if (idempotencyKey != null) {
            // 同一会员重复提交同一幂等键时直接返回原订单，避免重复扣库存。
            TradeOrder existing = tradeOrderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                    .eq(TradeOrder::getMemberId, request.memberId())
                    .eq(TradeOrder::getIdempotencyKey, idempotencyKey)
                    .last("LIMIT 1 FOR UPDATE"));
            if (existing != null) {
                return existing;
            }
        }
        validateAmounts(request);
        validateCatalogItems(request);
        TradeOrder order = new TradeOrder();
        order.setOrderNo("AO" + IdWorker.getIdStr());
        order.setIdempotencyKey(idempotencyKey);
        order.setMemberId(request.memberId());
        // 下单阶段只完成库存预占，必须等待支付回调后再进入待发货和已支付状态。
        order.setOrderStatus(TradeOrderStateMachine.STATUS_PENDING_PAYMENT);
        order.setPaymentStatus(0);
        order.setPaymentMethod(request.paymentMethod());
        order.setSubtotalAmount(request.subtotalAmount());
        order.setDiscountAmount(request.discountAmount());
        order.setFreightAmount(request.freightAmount());
        order.setPayableAmount(request.payableAmount());
        order.setPaidAmount(BigDecimal.ZERO);
        order.setReceiverName(request.receiverName());
        order.setReceiverPhone(request.receiverPhone());
        order.setReceiverProvince(request.receiverProvince());
        order.setReceiverCity(request.receiverCity());
        order.setReceiverDistrict(request.receiverDistrict());
        order.setReceiverAddress(request.receiverAddress());
        order.setPaidAt(null);
        try {
            tradeOrderMapper.insert(order);
        } catch (DuplicateKeyException exception) {
            if (idempotencyKey != null) {
                // 并发请求由数据库唯一索引仲裁，冲突方读取并返回已创建订单。
                TradeOrder existing = tradeOrderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getMemberId, request.memberId())
                        .eq(TradeOrder::getIdempotencyKey, idempotencyKey)
                        .last("LIMIT 1 FOR UPDATE"));
                if (existing != null) {
                    return existing;
                }
            }
            throw exception;
        }
        for (TradeOrderCreateRequest.Item itemRequest : request.items()) {
            CatalogProduct product = catalogProductMapper.selectById(itemRequest.productId());
            CatalogSku sku = itemRequest.skuId() == null ? null : catalogSkuMapper.selectById(itemRequest.skuId());
            TradeOrderItem item = new TradeOrderItem();
            item.setOrderId(order.getId());
            item.setProductId(itemRequest.productId());
            item.setSkuId(itemRequest.skuId());
            item.setProductName(product.getProductName());
            item.setSkuName(sku == null ? itemRequest.skuName() : sku.getSkuName());
            item.setSkuCode(sku == null ? itemRequest.skuCode() : sku.getSkuCode());
            item.setImageUrl(StringUtils.hasText(product.getMainImageUrl()) ? product.getMainImageUrl() : itemRequest.imageUrl());
            item.setUnitPrice(itemRequest.unitPrice());
            item.setQuantity(itemRequest.quantity());
            item.setItemAmount(itemRequest.unitPrice().multiply(BigDecimal.valueOf(itemRequest.quantity())));
            tradeOrderItemMapper.insert(item);
        }
        // 订单和库存预占处于同一事务，库存不足时整个订单创建会回滚。
        inventoryStockService.reserve(order.getId(), order.getOrderNo(), request.items().stream()
                .map(item -> new InventoryReservationItem(item.productId(), item.skuId(), item.quantity()))
                .toList());
        tradeEventOutboxService.recordOrderCreated(order);
        return order;
    }

    /**
     * 关闭超时未支付订单并释放库存。
     *
     * @param timeoutMinutes 支付超时时间（分钟）
     * @return 本次关闭的订单数量
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public int closeExpiredOrders(int timeoutMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(timeoutMinutes, 1));
        List<TradeOrder> orders = tradeOrderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderStatus, TradeOrderStateMachine.STATUS_PENDING_PAYMENT)
                .eq(TradeOrder::getPaymentStatus, 0)
                .le(TradeOrder::getCreatedAt, cutoff)
                .orderByAsc(TradeOrder::getCreatedAt)
                .last("LIMIT 100"));
        int closedCount = 0;
        for (TradeOrder order : orders) {
            // 乐观锁更新失败说明订单已被支付或人工取消，本轮跳过并交给下一次扫描。
            TradeOrderStateMachine.requireTransition(order.getOrderStatus(), TradeOrderStateMachine.STATUS_CANCELLED);
            order.setOrderStatus(TradeOrderStateMachine.STATUS_CANCELLED);
            order.setRemark("系统关闭：订单超过支付时限");
            if (tradeOrderMapper.updateById(order) == 0) {
                continue;
            }
            inventoryStockService.release(order.getId(), order.getOrderNo());
            tradeEventOutboxService.recordOrderCancelled(order, true);
            closedCount++;
        }
        return closedCount;
    }

    /**
     * 校验订单明细与金额汇总的一致性。
     *
     * @param request 订单创建请求
     * @author Henfon
     * @date 2026-08-30
     */
    private void validateAmounts(TradeOrderCreateRequest request) {
        // 先按明细单价和数量重算商品小计，拒绝客户端直接篡改汇总金额。
        BigDecimal calculatedSubtotal = request.items().stream()
                .map(item -> {
                    if (item.unitPrice().signum() < 0) {
                        throw new BusinessException("TRADE_PRICE_INVALID", "商品单价不能为负数");
                    }
                    return item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subtotal = money(request.subtotalAmount());
        BigDecimal discount = money(request.discountAmount());
        BigDecimal freight = money(request.freightAmount());
        BigDecimal payable = money(request.payableAmount());
        if (discount.signum() < 0 || freight.signum() < 0) {
            throw new BusinessException("TRADE_AMOUNT_INVALID", "优惠金额和运费不能为负数");
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new BusinessException("TRADE_DISCOUNT_INVALID", "优惠金额不能超过商品小计");
        }
        if (money(calculatedSubtotal).compareTo(subtotal) != 0) {
            throw new BusinessException("TRADE_SUBTOTAL_MISMATCH", "商品小计与明细金额不一致");
        }
        BigDecimal calculatedPayable = subtotal.subtract(discount).add(freight);
        if (calculatedPayable.compareTo(payable) != 0) {
            throw new BusinessException("TRADE_PAYABLE_MISMATCH", "应付金额与订单优惠、运费不一致");
        }
    }

    /**
     * 按商品目录事实校验商品、SKU、上下架状态、库存和成交单价。
     *
     * @param request 订单创建请求
     * @author Henfon
     * @date 2026-08-30
     */
    private void validateCatalogItems(TradeOrderCreateRequest request) {
        for (TradeOrderCreateRequest.Item item : request.items()) {
            CatalogProduct product = item.productId() == null ? null : catalogProductMapper.selectById(item.productId());
            if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
                throw new BusinessException("TRADE_PRODUCT_UNAVAILABLE", "商品不存在或已下架");
            }
            BigDecimal actualPrice = product.getPrice();
            Integer stock = product.getCurrentStock();
            if (item.skuId() != null) {
                CatalogSku sku = catalogSkuMapper.selectById(item.skuId());
                if (sku == null || !item.productId().equals(sku.getProductId()) || !Integer.valueOf(1).equals(sku.getStatus())) {
                    throw new BusinessException("TRADE_SKU_UNAVAILABLE", "商品规格不存在或已停用");
                }
                actualPrice = sku.getPrice();
                stock = sku.getStock();
            }
            if (actualPrice == null || item.unitPrice() == null || actualPrice.setScale(2, RoundingMode.HALF_UP)
                    .compareTo(item.unitPrice().setScale(2, RoundingMode.HALF_UP)) != 0) {
                throw new BusinessException("TRADE_PRICE_CHANGED", "商品价格已变化，请刷新后重试");
            }
            if (stock == null || stock < item.quantity()) {
                throw new BusinessException("TRADE_STOCK_NOT_ENOUGH", "商品库存不足，请减少购买数量");
            }
        }
    }

    /**
     * 将金额统一为两位小数。
     *
     * @param amount 原始金额
     * @return 两位小数金额
     * @author Henfon
     * @date 2026-08-30
     */
    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
