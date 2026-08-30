package com.henfon.shop.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.trade.dto.TradeCartItemRequest;
import com.henfon.shop.trade.dto.TradeCartItemUpdateRequest;
import com.henfon.shop.trade.entity.TradeCartItem;
import com.henfon.shop.trade.mapper.TradeCartItemMapper;
import com.henfon.shop.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 门户购物车服务。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class TradeCartService {
    private final TradeCartItemMapper mapper;

    /**
     * 创建购物车服务。
     *
     * @param mapper 购物车数据访问对象
     * @author Henfon
     * @date 2026-08-29
     */
    public TradeCartService(TradeCartItemMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询会员购物车。
     *
     * @param memberId 会员ID
     * @return 购物车列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<TradeCartItem> list(Long memberId) {
        return mapper.selectList(new LambdaQueryWrapper<TradeCartItem>()
                .eq(TradeCartItem::getMemberId, memberId).orderByDesc(TradeCartItem::getUpdatedAt));
    }

    /**
     * 新增或合并购物车商品。
     *
     * @param request 购物车请求
     * @return 购物车明细ID
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public Long add(TradeCartItemRequest request) {
        LambdaQueryWrapper<TradeCartItem> query = new LambdaQueryWrapper<TradeCartItem>()
                .eq(TradeCartItem::getMemberId, request.memberId())
                .eq(TradeCartItem::getProductId, request.productId());
        // SKU 为空时必须匹配 NULL，避免同一商品不同规格被错误合并。
        if (request.skuId() == null) {
            query.isNull(TradeCartItem::getSkuId);
        } else {
            query.eq(TradeCartItem::getSkuId, request.skuId());
        }
        TradeCartItem item = mapper.selectOne(query);
        if (item == null) {
            item = new TradeCartItem();
            item.setMemberId(request.memberId());
            item.setProductId(request.productId());
            item.setSkuId(request.skuId());
            item.setQuantity(request.quantity());
            item.setSelected(request.selected() == null ? 1 : request.selected());
            mapper.insert(item);
        } else {
            item.setQuantity(item.getQuantity() + request.quantity());
            item.setSelected(request.selected() == null ? item.getSelected() : request.selected());
            mapper.updateById(item);
        }
        return item.getId();
    }

    /**
     * 删除购物车明细。
     *
     * @param id 明细ID
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public void delete(Long memberId, Long id) {
        TradeCartItem item = requireMemberItem(memberId, id);
        mapper.deleteById(item.getId());
    }

    /**
     * 更新购物车数量或选中状态。
     *
     * @param id 明细ID
     * @param request 更新请求
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public void update(Long memberId, Long id, TradeCartItemUpdateRequest request) {
        TradeCartItem item = requireMemberItem(memberId, id);
        if (request.quantity() != null) {
            item.setQuantity(request.quantity());
        }
        if (request.selected() != null) {
            item.setSelected(request.selected());
        }
        mapper.updateById(item);
    }

    /**
     * 查询并校验购物车明细归属。
     *
     * @param memberId 会员ID
     * @param id 明细ID
     * @return 购物车明细
     * @author Henfon
     * @date 2026-08-30
     */
    private TradeCartItem requireMemberItem(Long memberId, Long id) {
        TradeCartItem item = mapper.selectOne(new LambdaQueryWrapper<TradeCartItem>()
                .eq(TradeCartItem::getId, id)
                .eq(TradeCartItem::getMemberId, memberId));
        if (item == null) {
            throw new BusinessException("TRADE_CART_ITEM_NOT_FOUND", "购物车明细不存在或不属于当前会员");
        }
        return item;
    }
}
