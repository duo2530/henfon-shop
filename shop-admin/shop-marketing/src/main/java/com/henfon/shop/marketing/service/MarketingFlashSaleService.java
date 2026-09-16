package com.henfon.shop.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.common.marketing.FlashSaleReservationItem;
import com.henfon.shop.common.marketing.FlashSaleReservationService;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.entity.CatalogSku;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.catalog.mapper.CatalogSkuMapper;
import com.henfon.shop.marketing.dto.MarketingFlashSaleSaveRequest;
import com.henfon.shop.marketing.entity.MarketingFlashSale;
import com.henfon.shop.marketing.entity.MarketingFlashSaleItem;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleItemMapper;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleMapper;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleReservationMapper;
import com.henfon.shop.marketing.entity.MarketingFlashSaleReservation;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 秒杀促销活动管理服务。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class MarketingFlashSaleService implements FlashSaleReservationService {

    private static final Logger log = LoggerFactory.getLogger(MarketingFlashSaleService.class);

    private final MarketingFlashSaleMapper activityMapper;
    private final MarketingFlashSaleItemMapper itemMapper;
    private final MarketingFlashSaleReservationMapper reservationMapper;
    private final CatalogProductMapper productMapper;
    private final CatalogSkuMapper skuMapper;

    /**
     * 创建秒杀活动服务。
     *
     * @param activityMapper 活动数据访问对象
     * @param itemMapper 活动商品数据访问对象
     * @param reservationMapper 秒杀预占数据访问对象
     * @param productMapper 商品目录数据访问对象
     * @param skuMapper 商品SKU数据访问对象
     * @author Henfon
     * @date 2026-08-31
     */
    public MarketingFlashSaleService(MarketingFlashSaleMapper activityMapper, MarketingFlashSaleItemMapper itemMapper,
                                     MarketingFlashSaleReservationMapper reservationMapper,
                                     CatalogProductMapper productMapper, CatalogSkuMapper skuMapper) {
        this.activityMapper = activityMapper;
        this.itemMapper = itemMapper;
        this.reservationMapper = reservationMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
    }

    /**
     * 预占秒杀活动库存并校验会员限购。
     *
     * @param activityId 活动ID
     * @param memberId 会员ID
     * @param orderId 订单ID
     * @param items 活动商品
     * @author Henfon
     * @date 2026-09-01
     */
    @Override
    @Transactional
    public void reserve(Long activityId, Long memberId, Long orderId, List<FlashSaleReservationItem> items) {
        if (activityId == null || memberId == null || orderId == null || items == null || items.isEmpty()) {
            throw new BusinessException("MARKETING_FLASH_SALE_REQUEST_INVALID", "秒杀活动和商品不能为空");
        }
        // 订单创建可能因网络重试再次进入预占流程，已有有效预占时直接返回，避免重复扣减活动库存。
        List<MarketingFlashSaleReservation> existingReservations = reservationMapper.selectList(
                new LambdaQueryWrapper<MarketingFlashSaleReservation>()
                        .eq(MarketingFlashSaleReservation::getOrderId, orderId)
                        .eq(MarketingFlashSaleReservation::getStatus, 0)
                        .last("LIMIT 1"));
        if (existingReservations != null && !existingReservations.isEmpty()) {
            MarketingFlashSaleReservation existing = existingReservations.get(0);
            if (!activityId.equals(existing.getActivityId()) || !memberId.equals(existing.getMemberId())) {
                throw new BusinessException("MARKETING_FLASH_SALE_ORDER_CONFLICT", "订单已关联其他秒杀预占");
            }
            return;
        }
        MarketingFlashSale activity = activityMapper.selectById(activityId);
        LocalDateTime now = LocalDateTime.now();
        if (activity == null || !Integer.valueOf(1).equals(activity.getStatus())
                || now.isBefore(activity.getStartAt()) || now.isAfter(activity.getEndAt())) {
            throw new BusinessException("MARKETING_FLASH_SALE_NOT_ACTIVE", "秒杀活动未开始或已结束");
        }
        for (FlashSaleReservationItem requestItem : items) {
            if (requestItem == null || requestItem.quantity() < 1) {
                throw new BusinessException("MARKETING_FLASH_SALE_QUANTITY_INVALID", "秒杀购买数量必须大于 0");
            }
            MarketingFlashSaleItem item = matchActivityItem(activityId, requestItem.productId(), requestItem.skuId());
            if (item == null) {
                throw new BusinessException("MARKETING_FLASH_SALE_ITEM_INVALID", "商品不在当前秒杀活动中");
            }
            if (requestItem.unitPrice() == null || item.getActivityPrice() == null
                    || item.getActivityPrice().setScale(2).compareTo(requestItem.unitPrice().setScale(2)) != 0) {
                throw new BusinessException("MARKETING_FLASH_SALE_PRICE_CHANGED", "秒杀价格已变化，请刷新后重试");
            }
            int memberBought = reservationMapper.selectList(new LambdaQueryWrapper<MarketingFlashSaleReservation>()
                    .eq(MarketingFlashSaleReservation::getActivityId, activityId)
                    .eq(MarketingFlashSaleReservation::getActivityItemId, item.getId())
                    .eq(MarketingFlashSaleReservation::getMemberId, memberId)
                    .eq(MarketingFlashSaleReservation::getStatus, 0)).stream()
                    .mapToInt(MarketingFlashSaleReservation::getQuantity).sum();
            int limit = Math.min(activity.getLimitPerMember(), item.getLimitPerMember());
            if (memberBought + requestItem.quantity() > limit) {
                throw new BusinessException("MARKETING_FLASH_SALE_LIMIT_EXCEEDED", "已超过该活动的会员限购数量");
            }
            int updated = itemMapper.update(null, new LambdaUpdateWrapper<MarketingFlashSaleItem>()
                    .setSql("sold_stock = sold_stock + " + requestItem.quantity())
                    .eq(MarketingFlashSaleItem::getId, item.getId())
                    .apply("sold_stock + {0} <= total_stock", requestItem.quantity()));
            if (updated == 0) {
                throw new BusinessException("MARKETING_FLASH_SALE_STOCK_NOT_ENOUGH", "秒杀活动库存不足");
            }
            MarketingFlashSaleReservation reservation = new MarketingFlashSaleReservation();
            reservation.setActivityId(activityId);
            reservation.setActivityItemId(item.getId());
            reservation.setMemberId(memberId);
            reservation.setOrderId(orderId);
            reservation.setQuantity(requestItem.quantity());
            reservation.setStatus(0);
            reservationMapper.insert(reservation);
        }
    }

    /**
     * 匹配活动明细：优先命中指定 SKU，未命中时回退到按商品配置的通配明细。
     *
     * <p>门户下单链路必须先通过库存预占，而库存预占要求订单明细指定具体 SKU，
     * 因此请求中的 skuId 恒不为空；管理端又允许按商品维度配置活动明细（sku_id 为空，
     * 语义是该商品任意 SKU 都参与活动）。两者叠加时若只做精确匹配，通配明细永远命不中，
     * 会员下单只会收到「商品不在当前秒杀活动中」。</p>
     *
     * @param activityId 活动ID
     * @param productId 商品ID
     * @param skuId 请求携带的SKU ID，可为空
     * @return 命中的活动明细，未命中返回 null
     * @author Henfon
     * @date 2026-09-16
     */
    private MarketingFlashSaleItem matchActivityItem(Long activityId, Long productId, Long skuId) {
        if (skuId != null) {
            MarketingFlashSaleItem exact = itemMapper.selectOne(new LambdaQueryWrapper<MarketingFlashSaleItem>()
                    .eq(MarketingFlashSaleItem::getActivityId, activityId)
                    .eq(MarketingFlashSaleItem::getProductId, productId)
                    .eq(MarketingFlashSaleItem::getSkuId, skuId)
                    .eq(MarketingFlashSaleItem::getStatus, 1)
                    .last("LIMIT 1 FOR UPDATE"));
            if (exact != null) {
                return exact;
            }
        }
        return itemMapper.selectOne(new LambdaQueryWrapper<MarketingFlashSaleItem>()
                .eq(MarketingFlashSaleItem::getActivityId, activityId)
                .eq(MarketingFlashSaleItem::getProductId, productId)
                .isNull(MarketingFlashSaleItem::getSkuId)
                .eq(MarketingFlashSaleItem::getStatus, 1)
                .last("LIMIT 1 FOR UPDATE"));
    }

    /**
     * 释放取消订单的秒杀库存。
     *
     * @param orderId 订单ID
     * @author Henfon
     * @date 2026-09-01
     */
    @Override
    @Transactional
    public void release(Long orderId) {
        if (orderId == null) {
            return;
        }
        List<MarketingFlashSaleReservation> reservations = reservationMapper.selectList(
                new LambdaQueryWrapper<MarketingFlashSaleReservation>()
                        .eq(MarketingFlashSaleReservation::getOrderId, orderId)
                        .eq(MarketingFlashSaleReservation::getStatus, 0)
                        .last("FOR UPDATE"));
        if (reservations == null || reservations.isEmpty()) {
            // 没有活动预占时无需执行后续更新，兼容普通订单和历史脏数据。
            return;
        }
        for (MarketingFlashSaleReservation reservation : reservations) {
            itemMapper.update(null, new LambdaUpdateWrapper<MarketingFlashSaleItem>()
                    .setSql("sold_stock = GREATEST(sold_stock - " + reservation.getQuantity() + ", 0)")
                    .eq(MarketingFlashSaleItem::getId, reservation.getActivityItemId()));
            reservation.setStatus(1);
            reservation.setReleasedAt(LocalDateTime.now());
            reservationMapper.updateById(reservation);
        }
    }

    /**
     * 预热即将开始的秒杀活动并收口已结束状态。
     *
     * @param warmupMinutes 预热时间窗口（分钟）
     * @return 本轮处理的活动数量
     * @author Henfon
     * @date 2026-09-04
     */
    @Transactional
    public int warmupUpcomingActivities(int warmupMinutes) {
        int safeMinutes = Math.max(warmupMinutes, 0);
        LocalDateTime now = LocalDateTime.now();
        // 先将已结束的启用活动收口，避免门户继续暴露过期活动。
        List<MarketingFlashSale> expired = activityMapper.selectList(new LambdaQueryWrapper<MarketingFlashSale>()
                .eq(MarketingFlashSale::getStatus, 1)
                .lt(MarketingFlashSale::getEndAt, now));
        if (expired != null) {
            for (MarketingFlashSale activity : expired) {
                activity.setStatus(2);
                activityMapper.updateById(activity);
            }
        }
        LocalDateTime windowEnd = now.plusMinutes(safeMinutes);
        List<MarketingFlashSale> upcoming = activityMapper.selectList(new LambdaQueryWrapper<MarketingFlashSale>()
                .eq(MarketingFlashSale::getStatus, 1)
                .gt(MarketingFlashSale::getStartAt, now)
                .le(MarketingFlashSale::getStartAt, windowEnd));
        if (upcoming == null || upcoming.isEmpty()) {
            return 0;
        }
        int processed = 0;
        for (MarketingFlashSale activity : upcoming) {
            List<MarketingFlashSaleItem> items = itemMapper.selectList(new LambdaQueryWrapper<MarketingFlashSaleItem>()
                    .eq(MarketingFlashSaleItem::getActivityId, activity.getId())
                    .eq(MarketingFlashSaleItem::getStatus, 1));
            boolean valid = items != null && !items.isEmpty();
            if (valid) {
                for (MarketingFlashSaleItem item : items) {
                    Integer total = item.getTotalStock();
                    Integer sold = item.getSoldStock();
                    if (total == null || total < 0 || (sold != null && sold < 0) || (sold != null && sold > total)) {
                        valid = false;
                        log.warn("秒杀活动预热库存校验失败，activityId={}, itemId={}, totalStock={}, soldStock={}",
                                activity.getId(), item.getId(), total, sold);
                        continue;
                    }
                    if (sold == null) {
                        // 历史数据可能没有已售快照，预热阶段补齐为 0，保证扣减表达式可安全执行。
                        item.setSoldStock(0);
                        itemMapper.updateById(item);
                    }
                }
            }
            if (valid) {
                processed++;
                log.info("秒杀活动预热完成，activityId={}, startAt={}, itemCount={}", activity.getId(),
                        activity.getStartAt(), items.size());
            } else {
                log.warn("秒杀活动预热跳过无效活动，activityId={}, startAt={}", activity.getId(), activity.getStartAt());
            }
        }
        return processed;
    }

    /**
     * 分页查询秒杀活动。
     *
     * @param keyword 活动编码或名称关键字
     * @param status 活动状态
     * @param current 页码
     * @param size 页大小
     * @return 活动分页数据
     * @author Henfon
     * @date 2026-08-31
     */
    public IPage<MarketingFlashSale> page(String keyword, Integer status, long current, long size) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        String normalized = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return activityMapper.selectPage(new Page<>(safeCurrent, safeSize), new LambdaQueryWrapper<MarketingFlashSale>()
                .and(normalized != null, wrapper -> wrapper
                        .like(MarketingFlashSale::getActivityCode, normalized)
                        .or().like(MarketingFlashSale::getActivityName, normalized))
                .eq(status != null, MarketingFlashSale::getStatus, status)
                .orderByDesc(MarketingFlashSale::getStartAt)
                .orderByDesc(MarketingFlashSale::getId));
    }

    /**
     * 查询活动商品明细。
     *
     * @param activityId 活动ID
     * @return 活动商品列表
     * @author Henfon
     * @date 2026-08-31
     */
    public List<MarketingFlashSaleItem> listItems(Long activityId) {
        requireActivity(activityId);
        return itemMapper.selectList(new LambdaQueryWrapper<MarketingFlashSaleItem>()
                .eq(MarketingFlashSaleItem::getActivityId, activityId)
                .orderByAsc(MarketingFlashSaleItem::getId));
    }

    /**
     * 保存秒杀活动及其商品明细。
     *
     * @param request 活动保存请求
     * @return 活动ID
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public Long save(MarketingFlashSaleSaveRequest request) {
        validate(request);
        MarketingFlashSale activity = request.id() == null ? new MarketingFlashSale() : requireActivity(request.id());
        if (request.id() != null && request.version() != null && !request.version().equals(activity.getVersion())) {
            throw new BusinessException("MARKETING_FLASH_SALE_CONCURRENT", "活动已被其他操作修改，请刷新后重试");
        }
        activity.setActivityCode(request.activityCode().trim().toUpperCase());
        activity.setActivityName(request.activityName().trim());
        activity.setStartAt(request.startAt());
        activity.setEndAt(request.endAt());
        activity.setLimitPerMember(request.limitPerMember());
        activity.setStatus(request.status());
        try {
            if (activity.getId() == null) {
                activityMapper.insert(activity);
            } else if (activityMapper.updateById(activity) == 0) {
                throw new BusinessException("MARKETING_FLASH_SALE_CONCURRENT", "活动已被其他操作修改，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("MARKETING_FLASH_SALE_CODE_EXISTS", "活动编码已存在");
        }
        // 覆盖保存前保留已售数量，避免编辑活动时把真实销量重置为 0。
        Map<String, Integer> soldStockByItem = new HashMap<>();
        if (activity.getId() != null) {
            itemMapper.selectList(new LambdaQueryWrapper<MarketingFlashSaleItem>()
                    .eq(MarketingFlashSaleItem::getActivityId, activity.getId()))
                    .forEach(item -> soldStockByItem.put(itemKey(item.getProductId(), item.getSkuId()),
                            item.getSoldStock() == null ? 0 : item.getSoldStock()));
        }
        // 明细采用事务内覆盖保存，避免删除后残留失效商品。
        itemMapper.delete(new LambdaQueryWrapper<MarketingFlashSaleItem>()
                .eq(MarketingFlashSaleItem::getActivityId, activity.getId()));
        for (MarketingFlashSaleSaveRequest.Item requestItem : request.items()) {
            MarketingFlashSaleItem item = new MarketingFlashSaleItem();
            item.setActivityId(activity.getId());
            item.setProductId(requestItem.productId());
            item.setSkuId(requestItem.skuId());
            item.setActivityPrice(requestItem.activityPrice());
            item.setTotalStock(requestItem.totalStock());
            int soldStock = soldStockByItem.getOrDefault(itemKey(requestItem.productId(), requestItem.skuId()), 0);
            if (requestItem.totalStock() < soldStock) {
                throw new BusinessException("MARKETING_FLASH_SALE_STOCK_INVALID", "活动库存不能低于已售库存");
            }
            validateAvailableStock(requestItem, soldStock);
            item.setSoldStock(soldStock);
            item.setLimitPerMember(requestItem.limitPerMember());
            item.setStatus(requestItem.status());
            itemMapper.insert(item);
        }
        return activity.getId();
    }

    /**
     * 修改活动启停状态。
     *
     * @param id 活动ID
     * @param status 目标状态
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1 && status != 2)) {
            throw new BusinessException("MARKETING_FLASH_SALE_STATUS_INVALID", "活动状态必须为 0、1 或 2");
        }
        MarketingFlashSale activity = requireActivity(id);
        activity.setStatus(status);
        if (activityMapper.updateById(activity) == 0) {
            throw new BusinessException("MARKETING_FLASH_SALE_CONCURRENT", "活动已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 逻辑删除活动及其明细。
     *
     * @param id 活动ID
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public void delete(Long id) {
        MarketingFlashSale activity = requireActivity(id);
        if (activity.getStatus() == 1 && activity.getStartAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("MARKETING_FLASH_SALE_DELETE_FORBIDDEN", "已开始的启用活动不允许删除");
        }
        itemMapper.delete(new LambdaQueryWrapper<MarketingFlashSaleItem>()
                .eq(MarketingFlashSaleItem::getActivityId, id));
        activityMapper.deleteById(id);
    }

    /**
     * 校验活动字段、时间窗口和商品明细。
     *
     * @param request 活动保存请求
     * @author Henfon
     * @date 2026-08-31
     */
    private void validate(MarketingFlashSaleSaveRequest request) {
        if (request.startAt().isAfter(request.endAt())) {
            throw new BusinessException("MARKETING_FLASH_SALE_TIME_INVALID", "活动开始时间不能晚于结束时间");
        }
        Set<String> uniqueItems = new HashSet<>();
        for (MarketingFlashSaleSaveRequest.Item item : request.items()) {
            if (item.activityPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("MARKETING_FLASH_SALE_PRICE_INVALID", "活动商品价格必须大于 0");
            }
            String key = item.productId() + ":" + (item.skuId() == null ? 0 : item.skuId());
            if (!uniqueItems.add(key)) {
                throw new BusinessException("MARKETING_FLASH_SALE_ITEM_DUPLICATE", "活动商品不可重复");
            }
            if (item.totalStock() < item.limitPerMember()) {
                throw new BusinessException("MARKETING_FLASH_SALE_STOCK_INVALID", "活动库存不能小于单会员限购数量");
            }
            CatalogProduct product = productMapper.selectById(item.productId());
            if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
                throw new BusinessException("MARKETING_FLASH_SALE_PRODUCT_INVALID", "秒杀商品不存在或未上架");
            }
            BigDecimal originalPrice = product.getPrice();
            if (item.skuId() != null) {
                CatalogSku sku = skuMapper.selectById(item.skuId());
                if (sku == null || !item.productId().equals(sku.getProductId()) || !Integer.valueOf(1).equals(sku.getStatus())) {
                    throw new BusinessException("MARKETING_FLASH_SALE_SKU_INVALID", "秒杀SKU不存在、未启用或不属于该商品");
                }
                originalPrice = sku.getPrice();
            }
            if (originalPrice == null || item.activityPrice().compareTo(originalPrice) > 0) {
                throw new BusinessException("MARKETING_FLASH_SALE_PRICE_INVALID", "活动价不能高于商品当前销售价");
            }
        }
    }

    /**
     * 校验本次新增的活动库存不能超过商品当前可用库存。
     *
     * @param item 活动商品请求
     * @param soldStock 已售活动库存
     * @author Henfon
     * @date 2026-09-12
     */
    private void validateAvailableStock(MarketingFlashSaleSaveRequest.Item item, int soldStock) {
        int availableStock;
        if (item.skuId() == null) {
            CatalogProduct product = productMapper.selectById(item.productId());
            availableStock = product == null || product.getCurrentStock() == null ? 0 : product.getCurrentStock();
        } else {
            CatalogSku sku = skuMapper.selectById(item.skuId());
            availableStock = sku == null || sku.getStock() == null ? 0 : sku.getStock();
        }
        if (item.totalStock() - soldStock > availableStock) {
            throw new BusinessException("MARKETING_FLASH_SALE_STOCK_INVALID", "活动库存不能超过商品当前可用库存");
        }
    }

    /**
     * 生成活动商品唯一匹配键。
     *
     * @param productId 商品ID
     * @param skuId SKU ID
     * @return 商品匹配键
     * @author Henfon
     * @date 2026-09-01
     */
    private String itemKey(Long productId, Long skuId) {
        return productId + ":" + (skuId == null ? 0 : skuId);
    }

    /**
     * 查询活动，不存在时抛出统一业务异常。
     *
     * @param id 活动ID
     * @return 活动实体
     * @author Henfon
     * @date 2026-08-31
     */
    private MarketingFlashSale requireActivity(Long id) {
        MarketingFlashSale activity = id == null ? null : activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException("MARKETING_FLASH_SALE_NOT_FOUND", "促销活动不存在");
        }
        return activity;
    }
}
