package com.henfon.shop.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.trade.dto.TradeCartItemRequest;
import com.henfon.shop.trade.dto.TradeCartItemUpdateRequest;
import com.henfon.shop.trade.entity.TradeCartItem;
import com.henfon.shop.trade.mapper.TradeCartItemMapper;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.entity.CatalogSku;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.catalog.mapper.CatalogSkuMapper;
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
    private final CatalogProductMapper productMapper;
    private final CatalogSkuMapper skuMapper;

    /**
     * 创建购物车服务。
     *
     * @param mapper 购物车数据访问对象
     * @author Henfon
     * @date 2026-08-29
     */
    public TradeCartService(TradeCartItemMapper mapper, CatalogProductMapper productMapper, CatalogSkuMapper skuMapper) {
        this.mapper = mapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
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
        validateProductAndSku(request.productId(), request.skuId(), request.quantity());
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
            int nextQuantity = item.getQuantity() + request.quantity();
            validateProductAndSku(request.productId(), request.skuId(), nextQuantity);
            item.setQuantity(nextQuantity);
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
            validateProductAndSku(item.getProductId(), item.getSkuId(), request.quantity());
            item.setQuantity(request.quantity());
        }
        if (request.selected() != null) {
            item.setSelected(request.selected());
        }
        mapper.updateById(item);
    }

    /**
     * 校验商品和 SKU 处于可售状态，并验证购物车数量不超过库存。
     *
     * @param productId 商品ID
     * @param skuId SKU ID
     * @param quantity 目标数量
     * @author Henfon
     * @date 2026-08-30
     */
    private void validateProductAndSku(Long productId, Long skuId, int quantity) {
        CatalogProduct product = productId == null ? null : productMapper.selectById(productId);
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            throw new BusinessException("TRADE_CART_PRODUCT_UNAVAILABLE", "商品不存在或已下架");
        }
        Integer stock = product.getCurrentStock();
        if (skuId != null) {
            CatalogSku sku = skuMapper.selectById(skuId);
            if (sku == null || !productId.equals(sku.getProductId()) || !Integer.valueOf(1).equals(sku.getStatus())) {
                throw new BusinessException("TRADE_CART_SKU_UNAVAILABLE", "商品规格不存在或已停用");
            }
            stock = sku.getStock();
        }
        if (quantity <= 0 || stock == null || quantity > stock) {
            throw new BusinessException("TRADE_CART_STOCK_NOT_ENOUGH", "商品库存不足");
        }
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
