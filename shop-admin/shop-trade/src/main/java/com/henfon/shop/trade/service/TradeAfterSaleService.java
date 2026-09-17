package com.henfon.shop.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.common.marketing.FlashSaleReservationService;
import com.henfon.shop.trade.dto.TradeAfterSaleAuditRequest;
import com.henfon.shop.trade.dto.TradeAfterSaleCreateRequest;
import com.henfon.shop.trade.dto.TradeOrderRefundRequest;
import com.henfon.shop.trade.entity.TradeAfterSale;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.entity.TradeOrderItem;
import com.henfon.shop.trade.mapper.TradeAfterSaleMapper;
import com.henfon.shop.trade.mapper.TradeOrderItemMapper;
import com.henfon.shop.trade.mapper.TradeOrderMapper;
import com.henfon.shop.integration.messaging.RocketMqTopics;
import com.henfon.shop.integration.storage.ImageReferenceResolver;
import com.henfon.shop.inventory.service.InventoryStockService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 交易售后应用服务，负责会员申请、后台审核和售后状态流转。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Service
public class TradeAfterSaleService {

    private static final int TYPE_REFUND_ONLY = 1;
    private static final int TYPE_RETURN_REFUND = 2;
    private static final int TYPE_EXCHANGE = 3;
    private static final int STATUS_PENDING_AUDIT = 10;
    private static final int STATUS_PROCESSING = 20;
    private static final int STATUS_COMPLETED = 30;
    private static final int STATUS_REJECTED = 40;
    private static final int STATUS_CANCELLED = 50;
    private static final int STATUS_REFUND_FAILED = 60;

    private final TradeAfterSaleMapper afterSaleMapper;
    private final TradeOrderMapper orderMapper;
    private final TradeOrderItemMapper orderItemMapper;
    private final TradeOrderService tradeOrderService;
    private final TradeEventOutboxService tradeEventOutboxService;
    private final InventoryStockService inventoryStockService;
    private final ObjectProvider<FlashSaleReservationService> flashSaleReservationServiceProvider;
    private final ImageReferenceResolver imageReferenceResolver;

    /**
     * 创建售后服务。
     *
     * @param afterSaleMapper 售后单数据访问对象
     * @param orderMapper 订单数据访问对象
     * @param orderItemMapper 订单明细数据访问对象
     * @param tradeOrderService 订单服务
     * @param tradeEventOutboxService 领域事件 Outbox 服务
     * @param inventoryStockService 库存台账服务
     * @param flashSaleReservationServiceProvider 秒杀预占服务，营销模块未启用时为空
     * @param imageReferenceResolver 存储引用解析器，负责凭证图片归一化与重签
     * @author Henfon
     * @date 2026-08-30
     */
    @Autowired
    public TradeAfterSaleService(TradeAfterSaleMapper afterSaleMapper, TradeOrderMapper orderMapper,
                                 TradeOrderItemMapper orderItemMapper, TradeOrderService tradeOrderService,
                                 TradeEventOutboxService tradeEventOutboxService,
                                 InventoryStockService inventoryStockService,
                                 ObjectProvider<FlashSaleReservationService> flashSaleReservationServiceProvider,
                                 ImageReferenceResolver imageReferenceResolver) {
        this.afterSaleMapper = afterSaleMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.tradeOrderService = tradeOrderService;
        this.tradeEventOutboxService = tradeEventOutboxService;
        this.inventoryStockService = inventoryStockService;
        this.flashSaleReservationServiceProvider = flashSaleReservationServiceProvider;
        this.imageReferenceResolver = imageReferenceResolver;
    }

    /**
     * 创建售后服务（兼容未配置对象存储的调用方，凭证地址按原样落库）。
     *
     * @param afterSaleMapper 售后单数据访问对象
     * @param orderMapper 订单数据访问对象
     * @param orderItemMapper 订单明细数据访问对象
     * @param tradeOrderService 订单服务
     * @param tradeEventOutboxService 领域事件 Outbox 服务
     * @param inventoryStockService 库存台账服务
     * @param flashSaleReservationServiceProvider 秒杀预占服务，营销模块未启用时为空
     * @author Henfon
     * @date 2026-08-30
     */
    public TradeAfterSaleService(TradeAfterSaleMapper afterSaleMapper, TradeOrderMapper orderMapper,
                                 TradeOrderItemMapper orderItemMapper, TradeOrderService tradeOrderService,
                                 TradeEventOutboxService tradeEventOutboxService,
                                 InventoryStockService inventoryStockService,
                                 ObjectProvider<FlashSaleReservationService> flashSaleReservationServiceProvider) {
        this(afterSaleMapper, orderMapper, orderItemMapper, tradeOrderService, tradeEventOutboxService,
                inventoryStockService, flashSaleReservationServiceProvider, null);
    }

    /**
     * 创建售后服务（兼容未配置领域事件 Outbox 的调用方）。
     *
     * @param afterSaleMapper 售后单数据访问对象
     * @param orderMapper 订单数据访问对象
     * @param orderItemMapper 订单明细数据访问对象
     * @param tradeOrderService 订单服务
     * @author Henfon
     * @date 2026-08-31
     */
    public TradeAfterSaleService(TradeAfterSaleMapper afterSaleMapper, TradeOrderMapper orderMapper,
                                 TradeOrderItemMapper orderItemMapper, TradeOrderService tradeOrderService) {
        this(afterSaleMapper, orderMapper, orderItemMapper, tradeOrderService, null, null, null);
    }

    /**
     * 会员申请售后。
     *
     * @param memberId 当前会员ID
     * @param orderId 订单ID
     * @param request 售后申请
     * @return 新建售后单
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public TradeAfterSale create(Long memberId, Long orderId, TradeAfterSaleCreateRequest request) {
        TradeOrder order = requireMemberOrder(memberId, orderId);
        validateOrderForApply(order, request.afterSaleType());
        TradeOrderItem item = request.orderItemId() == null ? null : requireOrderItem(orderId, request.orderItemId());
        BigDecimal refundAmount = request.refundAmount() == null ? BigDecimal.ZERO : request.refundAmount();
        validateRefundAmount(order, item, refundAmount, request.afterSaleType());
        if (hasActiveAfterSale(orderId, request.orderItemId())) {
            throw new BusinessException("TRADE_AFTER_SALE_DUPLICATE", "该订单或订单商品已有处理中售后申请");
        }
        TradeAfterSale afterSale = new TradeAfterSale();
        afterSale.setAfterSaleNo("AS" + IdWorker.getIdStr());
        afterSale.setOrderId(orderId);
        afterSale.setOrderItemId(request.orderItemId());
        afterSale.setMemberId(memberId);
        afterSale.setAfterSaleType(request.afterSaleType());
        afterSale.setStatus(STATUS_PENDING_AUDIT);
        afterSale.setReason(request.reason().trim());
        afterSale.setEvidenceUrls(serializeEvidenceUrls(request.evidenceUrls()));
        afterSale.setRefundAmount(refundAmount);
        afterSaleMapper.insert(afterSale);
        if (tradeEventOutboxService != null) {
            tradeEventOutboxService.recordAfterSaleEvent(afterSale, "AFTER_SALE_CREATED",
                    RocketMqTopics.AFTER_SALE_CREATED);
        }
        resignEvidenceUrls(afterSale);
        return afterSale;
    }

    /**
     * 查询会员售后单。
     *
     * @param memberId 当前会员ID
     * @return 售后单列表
     * @author Henfon
     * @date 2026-08-30
     */
    public List<TradeAfterSale> listByMember(Long memberId) {
        List<TradeAfterSale> afterSales = afterSaleMapper.selectList(new LambdaQueryWrapper<TradeAfterSale>()
                .eq(TradeAfterSale::getMemberId, memberId)
                .orderByDesc(TradeAfterSale::getCreatedAt));
        resignEvidenceUrls(afterSales);
        return afterSales;
    }

    /**
     * 后台分页查询售后单。
     *
     * @param status 售后状态，可选
     * @param orderId 订单ID，可选
     * @param current 当前页
     * @param size 页大小
     * @return 售后单分页结果
     * @author Henfon
     * @date 2026-08-30
     */
    public IPage<TradeAfterSale> page(Integer status, Long orderId, long current, long size) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        IPage<TradeAfterSale> page = afterSaleMapper.selectPage(new Page<>(safeCurrent, safeSize),
                new LambdaQueryWrapper<TradeAfterSale>()
                        .eq(status != null, TradeAfterSale::getStatus, status)
                        .eq(orderId != null, TradeAfterSale::getOrderId, orderId)
                        .orderByDesc(TradeAfterSale::getCreatedAt));
        resignEvidenceUrls(page.getRecords());
        return page;
    }

    /**
     * 后台审核通过售后单。
     *
     * @param afterSaleId 售后单ID
     * @param request 审核备注
     * @return 更新后的售后单
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public TradeAfterSale approve(Long afterSaleId, TradeAfterSaleAuditRequest request) {
        TradeAfterSale afterSale = requireAfterSale(afterSaleId);
        if (!Integer.valueOf(STATUS_PENDING_AUDIT).equals(afterSale.getStatus())) {
            throw new BusinessException("TRADE_AFTER_SALE_STATUS_INVALID", "仅待审核售后单允许审核通过");
        }
        if (TYPE_REFUND_ONLY == afterSale.getAfterSaleType()) {
            // 仅退款直接复用订单退款状态机，真实支付退款回调接入后再推进到最终完成。
            tradeOrderService.refund(afterSale.getOrderId(), new TradeOrderRefundRequest(
                    afterSale.getRefundAmount(), "售后审核通过"));
        }
        afterSale.setStatus(STATUS_PROCESSING);
        afterSale.setRemark(normalizeRemark(request == null ? null : request.remark(), "后台审核通过"));
        updateAfterSale(afterSale);
        if (tradeEventOutboxService != null) {
            tradeEventOutboxService.recordAfterSaleEvent(afterSale, "AFTER_SALE_APPROVED",
                    RocketMqTopics.AFTER_SALE_APPROVED);
        }
        return afterSale;
    }

    /**
     * 后台确认退货入库并触发原路退款。
     *
     * @param afterSaleId 售后单ID
     * @param remark 入库备注
     * @return 更新后的售后单
     * @author Henfon
     * @date 2026-09-01
     */
    @Transactional
    public TradeAfterSale confirmReturn(Long afterSaleId, String remark) {
        TradeAfterSale afterSale = requireAfterSale(afterSaleId);
        if (afterSale.getAfterSaleType() == null || afterSale.getAfterSaleType() != TYPE_RETURN_REFUND) {
            throw new BusinessException("TRADE_AFTER_SALE_TYPE_INVALID", "仅退货退款售后单支持退货入库确认");
        }
        if (!Integer.valueOf(STATUS_PROCESSING).equals(afterSale.getStatus())) {
            throw new BusinessException("TRADE_AFTER_SALE_STATUS_INVALID", "仅处理中退货退款售后单允许确认入库");
        }
        // 入库确认后复用订单退款状态机；重复点击时订单已处于退款中/已退款则安全跳过。
        if (inventoryStockService != null) {
            // 先回补库存并记录流水，再发起退款，确保仓库实物已确认后才允许资金流转。
            List<TradeOrderItem> returnItems;
            if (afterSale.getOrderItemId() == null) {
                // 整单退货需要将订单中的每个 SKU 分别回补，保证库存流水可按 SKU 对账。
                returnItems = orderItemMapper.selectList(new LambdaQueryWrapper<TradeOrderItem>()
                        .eq(TradeOrderItem::getOrderId, afterSale.getOrderId()));
            } else {
                returnItems = List.of(requireOrderItem(afterSale.getOrderId(), afterSale.getOrderItemId()));
            }
            if (returnItems.isEmpty()) {
                throw new BusinessException("TRADE_AFTER_SALE_ITEM_NOT_FOUND", "退货订单明细不存在，无法入库");
            }
            for (TradeOrderItem returnItem : returnItems) {
                if (returnItem.getSkuId() == null || returnItem.getQuantity() == null || returnItem.getQuantity() <= 0) {
                    throw new BusinessException("TRADE_AFTER_SALE_ITEM_INVALID", "退货订单明细缺少有效SKU或数量");
                }
                inventoryStockService.inboundReturn(returnItem.getSkuId(), returnItem.getQuantity(), afterSale.getAfterSaleNo());
            }
        }
        // 退货入库时同步释放秒杀活动名额：普通库存已回补，活动已售量若不减，这个名额就永久消失。
        // release 只处理未释放的预占且重复调用安全；退货粒度本身就是整条订单明细
        // （售后单没有数量字段，不支持部分数量退货），秒杀单又恒为单明细单件，整单释放即等价。
        if (flashSaleReservationServiceProvider != null) {
            FlashSaleReservationService flashSaleReservationService = flashSaleReservationServiceProvider.getIfAvailable();
            if (flashSaleReservationService != null) {
                flashSaleReservationService.release(afterSale.getOrderId());
            }
        }
        TradeOrder order = tradeOrderService.findById(afterSale.getOrderId());
        if (!Integer.valueOf(TradeOrderStateMachine.STATUS_REFUNDING).equals(order.getOrderStatus())
                && !Integer.valueOf(TradeOrderStateMachine.STATUS_REFUNDED).equals(order.getOrderStatus())) {
            tradeOrderService.refund(afterSale.getOrderId(), new TradeOrderRefundRequest(
                    afterSale.getRefundAmount(), StringUtils.hasText(remark) ? remark.trim() : "退货入库确认"));
        }
        afterSale.setRemark(StringUtils.hasText(remark) ? remark.trim() : "退货入库确认");
        updateAfterSale(afterSale);
        if (tradeEventOutboxService != null) {
            tradeEventOutboxService.recordAfterSaleEvent(afterSale, "AFTER_SALE_RETURN_RECEIVED",
                    RocketMqTopics.AFTER_SALE_RETURN_RECEIVED);
        }
        return afterSale;
    }

    /**
     * 后台驳回售后单。
     *
     * @param afterSaleId 售后单ID
     * @param request 驳回备注
     * @return 更新后的售后单
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public TradeAfterSale reject(Long afterSaleId, TradeAfterSaleAuditRequest request) {
        TradeAfterSale afterSale = requireAfterSale(afterSaleId);
        if (!Integer.valueOf(STATUS_PENDING_AUDIT).equals(afterSale.getStatus())) {
            throw new BusinessException("TRADE_AFTER_SALE_STATUS_INVALID", "仅待审核售后单允许驳回");
        }
        afterSale.setStatus(STATUS_REJECTED);
        afterSale.setRemark(normalizeRemark(request == null ? null : request.remark(), "后台驳回售后申请"));
        updateAfterSale(afterSale);
        if (tradeEventOutboxService != null) {
            tradeEventOutboxService.recordAfterSaleEvent(afterSale, "AFTER_SALE_REJECTED",
                    RocketMqTopics.AFTER_SALE_REJECTED);
        }
        return afterSale;
    }

    /**
     * 会员取消待审核售后单。
     *
     * @param memberId 当前会员ID
     * @param afterSaleId 售后单ID
     * @return 更新后的售后单
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public TradeAfterSale cancel(Long memberId, Long afterSaleId) {
        TradeAfterSale afterSale = requireMemberAfterSale(memberId, afterSaleId);
        if (!Integer.valueOf(STATUS_PENDING_AUDIT).equals(afterSale.getStatus())) {
            throw new BusinessException("TRADE_AFTER_SALE_STATUS_INVALID", "仅待审核售后单允许取消");
        }
        afterSale.setStatus(STATUS_CANCELLED);
        afterSale.setRemark("会员取消售后申请");
        updateAfterSale(afterSale);
        if (tradeEventOutboxService != null) {
            tradeEventOutboxService.recordAfterSaleEvent(afterSale, "AFTER_SALE_CANCELLED",
                    RocketMqTopics.AFTER_SALE_CANCELLED);
        }
        return afterSale;
    }

    /**
     * 退款成功后完成对应的仅退款售后单。
     *
     * <p>支付回调可能重复到达，因此只锁定处理中记录并将状态推进一次；找不到匹配记录时视为幂等空操作。</p>
     *
     * @param orderId 订单ID
     * @param refundAmount 实际退款金额
     * @return 是否完成了售后单
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public boolean markRefundSucceeded(Long orderId, BigDecimal refundAmount) {
        TradeAfterSale afterSale = findProcessingRefundAfterSale(orderId, refundAmount);
        if (afterSale == null) {
            return false;
        }
        afterSale.setStatus(STATUS_COMPLETED);
        afterSale.setRemark("退款成功，售后单已完成，金额：" + refundAmount);
        updateAfterSale(afterSale);
        if (tradeEventOutboxService != null) {
            // 完成事件用于会员通知，重复回调不会重复写入，因为处理中记录已被消费。
            tradeEventOutboxService.recordAfterSaleEvent(afterSale, "AFTER_SALE_COMPLETED",
                    RocketMqTopics.AFTER_SALE_COMPLETED);
        }
        return true;
    }

    /**
     * 退款失败后关闭对应的仅退款售后单。
     *
     * <p>失败售后不再占用处理中唯一目标，会员可以重新提交申请；原始失败原因写入备注便于审计。</p>
     *
     * @param orderId 订单ID
     * @param refundAmount 退款金额
     * @return 是否更新了售后单
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public boolean markRefundFailed(Long orderId, BigDecimal refundAmount) {
        TradeAfterSale afterSale = findProcessingRefundAfterSale(orderId, refundAmount);
        if (afterSale == null) {
            return false;
        }
        afterSale.setStatus(STATUS_REFUND_FAILED);
        afterSale.setRemark("支付渠道退款失败，售后单可重新申请，金额：" + refundAmount);
        updateAfterSale(afterSale);
        return true;
    }

    /**
     * 校验订单是否满足售后申请条件。
     *
     * @param order 订单实体
     * @param afterSaleType 售后类型
     * @author Henfon
     * @date 2026-08-30
     */
    private void validateOrderForApply(TradeOrder order, Integer afterSaleType) {
        if (!Integer.valueOf(1).equals(order.getPaymentStatus())) {
            throw new BusinessException("TRADE_AFTER_SALE_PAYMENT_INVALID", "仅已支付订单允许申请售后");
        }
        if (!Integer.valueOf(TradeOrderStateMachine.STATUS_PENDING_SHIPMENT).equals(order.getOrderStatus())
                && !Integer.valueOf(TradeOrderStateMachine.STATUS_SHIPPED).equals(order.getOrderStatus())
                && !Integer.valueOf(TradeOrderStateMachine.STATUS_COMPLETED).equals(order.getOrderStatus())) {
            throw new BusinessException("TRADE_AFTER_SALE_ORDER_STATUS_INVALID", "当前订单状态不允许申请售后");
        }
        if (afterSaleType == null || afterSaleType < TYPE_REFUND_ONLY || afterSaleType > TYPE_EXCHANGE) {
            throw new BusinessException("TRADE_AFTER_SALE_TYPE_INVALID", "售后类型不合法");
        }
        if (afterSaleType > TYPE_REFUND_ONLY
                && Integer.valueOf(TradeOrderStateMachine.STATUS_PENDING_SHIPMENT).equals(order.getOrderStatus())) {
            throw new BusinessException("TRADE_AFTER_SALE_TYPE_INVALID", "待发货订单仅支持仅退款");
        }
    }

    /**
     * 校验退款金额是否不超过订单或订单明细可退金额。
     *
     * @param order 订单实体
     * @param item 订单明细，可为空
     * @param refundAmount 申请退款金额
     * @param afterSaleType 售后类型
     * @author Henfon
     * @date 2026-08-30
     */
    private void validateRefundAmount(TradeOrder order, TradeOrderItem item, BigDecimal refundAmount,
                                      Integer afterSaleType) {
        if (refundAmount.signum() < 0) {
            throw new BusinessException("TRADE_AFTER_SALE_AMOUNT_INVALID", "退款金额不能为负数");
        }
        if (TYPE_EXCHANGE == afterSaleType && refundAmount.signum() > 0) {
            throw new BusinessException("TRADE_AFTER_SALE_AMOUNT_INVALID", "换货申请不支持退款金额");
        }
        BigDecimal maxAmount = item == null ? order.getPaidAmount() : item.getItemAmount();
        if (maxAmount == null) {
            maxAmount = BigDecimal.ZERO;
        }
        if (refundAmount.compareTo(maxAmount) > 0) {
            throw new BusinessException("TRADE_AFTER_SALE_AMOUNT_INVALID", "退款金额不能超过可退款金额");
        }
        if (TYPE_REFUND_ONLY != afterSaleType && refundAmount.signum() == 0) {
            throw new BusinessException("TRADE_AFTER_SALE_AMOUNT_INVALID", "退货退款必须填写退款金额");
        }
        if (TYPE_REFUND_ONLY == afterSaleType && refundAmount.signum() == 0) {
            throw new BusinessException("TRADE_AFTER_SALE_AMOUNT_INVALID", "仅退款必须填写退款金额");
        }
    }

    /**
     * 查询并校验会员订单归属。
     *
     * @param memberId 会员ID
     * @param orderId 订单ID
     * @return 会员订单
     * @author Henfon
     * @date 2026-08-30
     */
    private TradeOrder requireMemberOrder(Long memberId, Long orderId) {
        TradeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getId, orderId).eq(TradeOrder::getMemberId, memberId));
        if (order == null) {
            throw new BusinessException("TRADE_ORDER_FORBIDDEN", "订单不存在或无权操作");
        }
        return order;
    }

    /**
     * 查询并校验订单明细归属。
     *
     * @param orderId 订单ID
     * @param orderItemId 订单明细ID
     * @return 订单明细
     * @author Henfon
     * @date 2026-08-30
     */
    private TradeOrderItem requireOrderItem(Long orderId, Long orderItemId) {
        TradeOrderItem item = orderItemMapper.selectOne(new LambdaQueryWrapper<TradeOrderItem>()
                .eq(TradeOrderItem::getId, orderItemId).eq(TradeOrderItem::getOrderId, orderId));
        if (item == null) {
            throw new BusinessException("TRADE_ORDER_ITEM_NOT_FOUND", "订单明细不存在或不属于该订单");
        }
        return item;
    }

    /**
     * 判断订单或指定明细是否已有处理中售后单。
     *
     * @param orderId 订单ID
     * @param orderItemId 订单明细ID
     * @return 是否存在处理中售后
     * @author Henfon
     * @date 2026-08-30
     */
    private boolean hasActiveAfterSale(Long orderId, Long orderItemId) {
        LambdaQueryWrapper<TradeAfterSale> wrapper = new LambdaQueryWrapper<TradeAfterSale>()
                .eq(TradeAfterSale::getOrderId, orderId)
                .in(TradeAfterSale::getStatus, STATUS_PENDING_AUDIT, STATUS_PROCESSING);
        if (orderItemId != null) {
            // 明细级申请需要同时避让订单级申请和同一明细的处理中申请。
            wrapper.and(query -> query.isNull(TradeAfterSale::getOrderItemId)
                    .or().eq(TradeAfterSale::getOrderItemId, orderItemId));
        }
        return afterSaleMapper.selectCount(wrapper) > 0;
    }

    /**
     * 查询与退款金额匹配的处理中退款售后单。
     *
     * @param orderId 订单ID
     * @param refundAmount 退款金额
     * @return 匹配的售后单，不存在时返回空值
     * @author Henfon
     * @date 2026-08-31
     */
    private TradeAfterSale findProcessingRefundAfterSale(Long orderId, BigDecimal refundAmount) {
        if (orderId == null || refundAmount == null || refundAmount.signum() <= 0) {
            return null;
        }
        // 金额参与匹配，避免同一订单多明细售后时把回调错误归属到其他售后单。
        // 仅退款和退货退款都会在资金退款成功后完成售后单，换货不创建退款单因此不参与匹配。
        return afterSaleMapper.selectOne(new LambdaQueryWrapper<TradeAfterSale>()
                .eq(TradeAfterSale::getOrderId, orderId)
                .in(TradeAfterSale::getAfterSaleType, TYPE_REFUND_ONLY, TYPE_RETURN_REFUND)
                .eq(TradeAfterSale::getStatus, STATUS_PROCESSING)
                .eq(TradeAfterSale::getRefundAmount, refundAmount)
                .orderByAsc(TradeAfterSale::getCreatedAt)
                .last("LIMIT 1 FOR UPDATE"));
    }

    /**
     * 查询售后单并校验存在。
     *
     * @param afterSaleId 售后单ID
     * @return 售后单实体
     * @author Henfon
     * @date 2026-08-30
     */
    private TradeAfterSale requireAfterSale(Long afterSaleId) {
        TradeAfterSale afterSale = afterSaleMapper.selectById(afterSaleId);
        if (afterSale == null) {
            throw new BusinessException("TRADE_AFTER_SALE_NOT_FOUND", "售后单不存在");
        }
        return afterSale;
    }

    /**
     * 查询并校验会员售后单归属。
     *
     * @param memberId 会员ID
     * @param afterSaleId 售后单ID
     * @return 售后单实体
     * @author Henfon
     * @date 2026-08-30
     */
    private TradeAfterSale requireMemberAfterSale(Long memberId, Long afterSaleId) {
        TradeAfterSale afterSale = afterSaleMapper.selectOne(new LambdaQueryWrapper<TradeAfterSale>()
                .eq(TradeAfterSale::getId, afterSaleId).eq(TradeAfterSale::getMemberId, memberId));
        if (afterSale == null) {
            throw new BusinessException("TRADE_AFTER_SALE_FORBIDDEN", "售后单不存在或无权操作");
        }
        return afterSale;
    }

    /**
     * 使用乐观锁更新售后单。
     *
     * @param afterSale 售后单实体
     * @author Henfon
     * @date 2026-08-30
     */
    private void updateAfterSale(TradeAfterSale afterSale) {
        if (afterSaleMapper.updateById(afterSale) == 0) {
            throw new BusinessException("TRADE_AFTER_SALE_CONCURRENT_UPDATE", "售后单已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 将售后凭证地址归一化后序列化为 JSON 数组字符串。
     *
     * <p>上传接口返回的是 24 小时过期的预签名地址，直接落库会导致次日凭证图片 403，
     * 因此写入前统一转成对象键。</p>
     *
     * @param evidenceUrls 凭证地址列表
     * @return JSON 数组字符串
     * @author Henfon
     * @date 2026-09-01
     */
    private String serializeEvidenceUrls(List<String> evidenceUrls) {
        if (evidenceUrls == null || evidenceUrls.isEmpty()) {
            return null;
        }
        if (imageReferenceResolver != null) {
            return imageReferenceResolver.normalizeJsonArray(evidenceUrls);
        }
        // 未接入对象存储的部署按原始地址落库。
        return evidenceUrls.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(url -> "\"" + url.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * 将售后单中的凭证引用重签为当前有效的访问地址。
     *
     * @param afterSale 售后单，可为空
     * @author Henfon
     * @date 2026-09-17
     */
    private void resignEvidenceUrls(TradeAfterSale afterSale) {
        if (imageReferenceResolver == null || afterSale == null) {
            return;
        }
        afterSale.setEvidenceUrls(imageReferenceResolver.resignJsonArray(afterSale.getEvidenceUrls()));
    }

    /**
     * 批量将售后单中的凭证引用重签为当前有效的访问地址。
     *
     * @param afterSales 售后单列表，可为空
     * @author Henfon
     * @date 2026-09-17
     */
    private void resignEvidenceUrls(List<TradeAfterSale> afterSales) {
        if (imageReferenceResolver == null || afterSales == null) {
            return;
        }
        afterSales.forEach(this::resignEvidenceUrls);
    }

    /**
     * 规范化审核备注。
     *
     * @param remark 原始备注
     * @param fallback 默认备注
     * @return 规范化备注
     * @author Henfon
     * @date 2026-08-30
     */
    private String normalizeRemark(String remark, String fallback) {
        return StringUtils.hasText(remark) ? remark.trim() : fallback;
    }
}
