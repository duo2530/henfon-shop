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
import java.util.ArrayList;

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
    @Transactional
    public List<TradeCartItem> list(Long memberId) {
        List<TradeCartItem> items = new ArrayList<>(mapper.selectList(new LambdaQueryWrapper<TradeCartItem>()
                .eq(TradeCartItem::getMemberId, memberId).orderByDesc(TradeCartItem::getUpdatedAt)));
        // 查询时主动清理已下架、规格失效或库存归零的明细，避免结算页继续展示不可购买商品。
        items.removeIf(item -> {
            if (!isAvailable(item)) {
                mapper.deleteById(item.getId());
                return true;
            }
            normalizeQuantityToStock(item);
            return false;
        });
        return items;
    }

    /**
     * 清理会员购物车中的失效明细。
     *
     * @param memberId 会员ID
     * @return 实际清理数量
     * @author Henfon
     * @date 2026-09-04
     */
    @Transactional
    public int cleanInvalid(Long memberId) {
        List<TradeCartItem> items = mapper.selectList(new LambdaQueryWrapper<TradeCartItem>()
                .eq(TradeCartItem::getMemberId, memberId));
        int removed = 0;
        // 显式清理接口与查询时清理规则保持一致，保证客户端可主动刷新购物车状态。
        for (TradeCartItem item : items) {
            if (!isAvailable(item)) {
                removed += mapper.deleteById(item.getId());
            } else {
                normalizeQuantityToStock(item);
            }
        }
        return removed;
    }

    /**
     * 将购物车数量收敛到当前可售库存，避免库存下降后用户继续持有超量明细。
     *
     * @param item 购物车明细
     * @author Henfon
     * @date 2026-09-04
     */
    private void normalizeQuantityToStock(TradeCartItem item) {
        Integer stock = resolveStock(item);
        if (stock == null || item.getQuantity() == null || item.getQuantity() <= stock) {
            return;
        }
        // 直接持久化收敛后的数量，使刷新页面和后续结算都使用同一份库存事实。
        item.setQuantity(stock);
        mapper.updateById(item);
    }

    /**
     * 查询购物车明细对应商品或 SKU 的可用库存。
     *
     * @param item 购物车明细
     * @return 当前库存
     * @author Henfon
     * @date 2026-09-04
     */
    private Integer resolveStock(TradeCartItem item) {
        CatalogProduct product = productMapper.selectById(item.getProductId());
        if (product == null) {
            return null;
        }
        if (item.getSkuId() == null) {
            return product.getCurrentStock();
        }
        CatalogSku sku = skuMapper.selectById(item.getSkuId());
        return sku == null || !item.getProductId().equals(sku.getProductId()) ? null : sku.getStock();
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
     * 判断购物车明细当前是否仍可售。
     *
     * @param item 购物车明细
     * @return 商品、规格和库存均有效时返回 true
     * @author Henfon
     * @date 2026-09-01
     */
    private boolean isAvailable(TradeCartItem item) {
        if (item == null || item.getProductId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
            return false;
        }
        CatalogProduct product = productMapper.selectById(item.getProductId());
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            return false;
        }
        Integer stock = product.getCurrentStock();
        if (item.getSkuId() != null) {
            CatalogSku sku = skuMapper.selectById(item.getSkuId());
            if (sku == null || !item.getProductId().equals(sku.getProductId())
                    || !Integer.valueOf(1).equals(sku.getStatus())) {
                return false;
            }
            stock = sku.getStock();
        }
        // 库存不足的明细保留给用户调整数量，只有库存归零才判定为失效并自动清理。
        return stock != null && stock > 0;
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
