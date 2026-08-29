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
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
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

    /**
     * 创建交易订单服务。
     *
     * @param tradeOrderMapper 订单数据访问对象
     * @author Henfon
     * @date 2026-08-29
     */
    public TradeOrderService(TradeOrderMapper tradeOrderMapper, TradeOrderItemMapper tradeOrderItemMapper,
                             TradeOrderLogisticsMapper tradeOrderLogisticsMapper) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.tradeOrderItemMapper = tradeOrderItemMapper;
        this.tradeOrderLogisticsMapper = tradeOrderLogisticsMapper;
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
        return order;
    }
}
