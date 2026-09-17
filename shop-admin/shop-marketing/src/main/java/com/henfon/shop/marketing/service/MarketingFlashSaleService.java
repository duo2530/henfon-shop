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
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.inventory.service.InventoryStockService;
import com.henfon.shop.marketing.dto.MarketingFlashSaleDetailResponse;
import com.henfon.shop.marketing.dto.MarketingFlashSaleItemRow;
import com.henfon.shop.marketing.dto.MarketingFlashSaleReservationRow;
import com.henfon.shop.marketing.dto.MarketingFlashSaleReservationSummary;
import com.henfon.shop.marketing.dto.MarketingFlashSaleSaveRequest;
import com.henfon.shop.marketing.dto.MarketingFlashSaleStockSummary;
import com.henfon.shop.marketing.entity.MarketingFlashSale;
import com.henfon.shop.marketing.entity.MarketingFlashSaleItem;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleItemMapper;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleMapper;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleReservationMapper;
import com.henfon.shop.marketing.entity.MarketingFlashSaleReservation;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.mapper.TradeOrderMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    /** 详情页与导出共用的单页上限，防止一次拉取过大的明细集合。 */
    private static final long MAX_PAGE_SIZE = 500L;

    private final MarketingFlashSaleMapper activityMapper;
    private final MarketingFlashSaleItemMapper itemMapper;
    private final MarketingFlashSaleReservationMapper reservationMapper;
    private final CatalogProductMapper productMapper;
    private final CatalogSkuMapper skuMapper;
    private final MemberUserMapper memberUserMapper;
    private final TradeOrderMapper tradeOrderMapper;
    private final InventoryStockService inventoryStockService;
    private final FlashSalePortalCache portalCache;

    /**
     * 创建秒杀活动服务。
     *
     * @param activityMapper 活动数据访问对象
     * @param itemMapper 活动商品数据访问对象
     * @param reservationMapper 秒杀预占数据访问对象
     * @param productMapper 商品目录数据访问对象
     * @param skuMapper 商品SKU数据访问对象
     * @param memberUserMapper 会员数据访问对象，用于详情页回填会员名称
     * @param tradeOrderMapper 订单数据访问对象，用于详情页回填订单编号
     * @param inventoryStockService 库存台账服务，用于按可用库存校验活动配额
     * @param portalCache 门户秒杀列表缓存，配置变更后需要主动清除
     * @author Henfon
     * @date 2026-08-31
     */
    public MarketingFlashSaleService(MarketingFlashSaleMapper activityMapper, MarketingFlashSaleItemMapper itemMapper,
                                     MarketingFlashSaleReservationMapper reservationMapper,
                                     CatalogProductMapper productMapper, CatalogSkuMapper skuMapper,
                                     MemberUserMapper memberUserMapper, TradeOrderMapper tradeOrderMapper,
                                     InventoryStockService inventoryStockService,
                                     FlashSalePortalCache portalCache) {
        this.activityMapper = activityMapper;
        this.itemMapper = itemMapper;
        this.reservationMapper = reservationMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.memberUserMapper = memberUserMapper;
        this.tradeOrderMapper = tradeOrderMapper;
        this.inventoryStockService = inventoryStockService;
        this.portalCache = portalCache;
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
                throw new BusinessException("MARKETING_FLASH_SALE_ITEM_INVALID",
                        "商品 #" + requestItem.productId() + " 不在当前秒杀活动中，秒杀订单不能混入其他商品");
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
     * 查询活动详情与汇总统计。
     *
     * <p>库存与预占两份统计各走一条聚合查询，不把明细和预占记录读进内存累加；活动不存在时
     * 与其它管理端接口一致地抛出业务异常，而不是返回空详情。</p>
     *
     * @param id 活动ID
     * @return 活动详情
     * @author Henfon
     * @date 2026-09-17
     */
    public MarketingFlashSaleDetailResponse detail(Long id) {
        MarketingFlashSale activity = requireActivity(id);
        MarketingFlashSaleStockSummary stock = itemMapper.summarizeStock(id);
        MarketingFlashSaleReservationSummary reservation = reservationMapper.summarize(id);
        long itemCount = stock == null ? 0L : zeroIfNull(stock.getItemCount());
        long totalStock = stock == null ? 0L : zeroIfNull(stock.getTotalStock());
        long soldStock = stock == null ? 0L : zeroIfNull(stock.getSoldStock());
        return new MarketingFlashSaleDetailResponse(activity.getId(), activity.getActivityCode(),
                activity.getActivityName(), activity.getStartAt(), activity.getEndAt(),
                activity.getLimitPerMember(), activity.getStatus(), activityStatusText(activity),
                activity.getCreatedAt(), activity.getUpdatedAt(),
                itemCount, totalStock, soldStock, Math.max(totalStock - soldStock, 0L),
                sellThroughRate(soldStock, totalStock),
                reservation == null ? 0L : zeroIfNull(reservation.getReservedQuantity()),
                reservation == null ? 0L : zeroIfNull(reservation.getReleasedQuantity()),
                reservation == null ? 0L : zeroIfNull(reservation.getParticipantCount()),
                reservation == null ? 0L : zeroIfNull(reservation.getReservationCount()),
                reservation == null ? 0L : zeroIfNull(reservation.getOrderCount()),
                reservation == null ? null : reservation.getEarliestReservedAt(),
                reservation == null ? null : reservation.getLatestReservedAt());
    }

    /**
     * 分页查询活动商品明细，并按批补齐商品与规格名称。
     *
     * <p>详情页与导出共用这一个查询，保证两处看到的名称、原价口径完全一致。</p>
     *
     * @param activityId 活动ID
     * @param current 页码
     * @param size 页大小
     * @return 活动商品明细分页
     * @author Henfon
     * @date 2026-09-17
     */
    public IPage<MarketingFlashSaleItemRow> pageItems(Long activityId, long current, long size) {
        requireActivity(activityId);
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        IPage<MarketingFlashSaleItem> page = itemMapper.selectPage(new Page<>(safeCurrent, safeSize),
                new LambdaQueryWrapper<MarketingFlashSaleItem>()
                        .eq(MarketingFlashSaleItem::getActivityId, activityId)
                        .orderByAsc(MarketingFlashSaleItem::getId));
        Page<MarketingFlashSaleItemRow> result = new Page<>(safeCurrent, safeSize, page.getTotal());
        result.setRecords(toItemRows(page.getRecords()));
        return result;
    }

    /**
     * 分页查询活动预占记录。
     *
     * <p>预占记录只存会员、订单与活动明细标识，这里统一补齐会员昵称、订单编号、商品与规格名称，
     * 页面无需再逐行回查。活动明细被覆盖保存后旧记录已逻辑删除，此时对应商品信息为空，
     * 数量与状态仍然照常展示。</p>
     *
     * @param activityId 活动ID
     * @param statusText 预占状态，取 reserved/released，为空表示不限
     * @param current 页码
     * @param size 页大小
     * @return 预占记录分页
     * @author Henfon
     * @date 2026-09-17
     */
    public IPage<MarketingFlashSaleReservationRow> pageReservations(Long activityId, String statusText,
                                                                   long current, long size) {
        requireActivity(activityId);
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Integer status = parseReservationStatus(statusText);
        IPage<MarketingFlashSaleReservation> page = reservationMapper.selectPage(new Page<>(safeCurrent, safeSize),
                new LambdaQueryWrapper<MarketingFlashSaleReservation>()
                        .eq(MarketingFlashSaleReservation::getActivityId, activityId)
                        .eq(status != null, MarketingFlashSaleReservation::getStatus, status)
                        .orderByDesc(MarketingFlashSaleReservation::getId));
        Page<MarketingFlashSaleReservationRow> result = new Page<>(safeCurrent, safeSize, page.getTotal());
        result.setRecords(toReservationRows(page.getRecords()));
        return result;
    }

    /**
     * 解析预占状态文本。
     *
     * <p>无法识别的取值直接报错而不是当作「全部」：静默放宽会让筛选条件悄悄失效，
     * 页面显示「预占中」却混进已释放记录，比直接失败更难排查。</p>
     *
     * @param statusText 状态文本
     * @return 状态数值，为空表示不限
     * @author Henfon
     * @date 2026-09-17
     */
    private Integer parseReservationStatus(String statusText) {
        if (!StringUtils.hasText(statusText)) {
            return null;
        }
        return switch (statusText.trim().toLowerCase(Locale.ROOT)) {
            case "reserved" -> 0;
            case "released" -> 1;
            default -> throw new BusinessException("MARKETING_FLASH_SALE_STATUS_INVALID",
                    "预占状态只支持 reserved 或 released");
        };
    }

    /**
     * 批量组装活动商品明细行。
     *
     * @param items 活动商品明细
     * @return 明细行列表
     * @author Henfon
     * @date 2026-09-17
     */
    private List<MarketingFlashSaleItemRow> toItemRows(List<MarketingFlashSaleItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<Long, CatalogProduct> products = loadProducts(items);
        Map<Long, CatalogSku> skus = loadSkus(items);
        return items.stream().map(item -> toItemRow(item, products, skus)).toList();
    }

    /**
     * 批量组装预占记录行。
     *
     * @param reservations 预占记录
     * @return 预占记录行列表
     * @author Henfon
     * @date 2026-09-17
     */
    private List<MarketingFlashSaleReservationRow> toReservationRows(List<MarketingFlashSaleReservation> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return List.of();
        }
        Map<Long, MarketingFlashSaleItem> itemById = loadActivityItems(reservations);
        List<MarketingFlashSaleItem> items = new ArrayList<>(itemById.values());
        Map<Long, CatalogProduct> products = loadProducts(items);
        Map<Long, CatalogSku> skus = loadSkus(items);
        Map<Long, MemberUser> members = loadMembers(reservations);
        Map<Long, TradeOrder> orders = loadOrders(reservations);
        return reservations.stream()
                .map(reservation -> toReservationRow(reservation, itemById.get(reservation.getActivityItemId()),
                        products, skus, members, orders))
                .toList();
    }

    /**
     * 按活动明细标识批量加载明细。
     *
     * @param reservations 预占记录
     * @return 明细ID到明细的映射
     * @author Henfon
     * @date 2026-09-17
     */
    private Map<Long, MarketingFlashSaleItem> loadActivityItems(List<MarketingFlashSaleReservation> reservations) {
        List<Long> itemIds = reservations.stream().map(MarketingFlashSaleReservation::getActivityItemId)
                .filter(Objects::nonNull).distinct().toList();
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, MarketingFlashSaleItem> result = new HashMap<>();
        for (MarketingFlashSaleItem item : itemMapper.selectBatchIds(itemIds)) {
            result.put(item.getId(), item);
        }
        return result;
    }

    /**
     * 按商品标识批量加载商品。
     *
     * @param items 活动商品明细
     * @return 商品ID到商品的映射
     * @author Henfon
     * @date 2026-09-17
     */
    private Map<Long, CatalogProduct> loadProducts(List<MarketingFlashSaleItem> items) {
        List<Long> productIds = items.stream().map(MarketingFlashSaleItem::getProductId)
                .filter(Objects::nonNull).distinct().toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, CatalogProduct> result = new HashMap<>();
        for (CatalogProduct product : productMapper.selectBatchIds(productIds)) {
            result.put(product.getId(), product);
        }
        return result;
    }

    /**
     * 按 SKU 标识批量加载规格。
     *
     * @param items 活动商品明细
     * @return SKU ID到SKU的映射
     * @author Henfon
     * @date 2026-09-17
     */
    private Map<Long, CatalogSku> loadSkus(List<MarketingFlashSaleItem> items) {
        List<Long> skuIds = items.stream().map(MarketingFlashSaleItem::getSkuId)
                .filter(Objects::nonNull).distinct().toList();
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, CatalogSku> result = new HashMap<>();
        for (CatalogSku sku : skuMapper.selectBatchIds(skuIds)) {
            result.put(sku.getId(), sku);
        }
        return result;
    }

    /**
     * 按会员标识批量加载会员。
     *
     * @param reservations 预占记录
     * @return 会员ID到会员的映射
     * @author Henfon
     * @date 2026-09-17
     */
    private Map<Long, MemberUser> loadMembers(List<MarketingFlashSaleReservation> reservations) {
        List<Long> memberIds = reservations.stream().map(MarketingFlashSaleReservation::getMemberId)
                .filter(Objects::nonNull).distinct().toList();
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, MemberUser> result = new HashMap<>();
        for (MemberUser member : memberUserMapper.selectBatchIds(memberIds)) {
            result.put(member.getId(), member);
        }
        return result;
    }

    /**
     * 按订单标识批量加载订单。
     *
     * @param reservations 预占记录
     * @return 订单ID到订单的映射
     * @author Henfon
     * @date 2026-09-17
     */
    private Map<Long, TradeOrder> loadOrders(List<MarketingFlashSaleReservation> reservations) {
        List<Long> orderIds = reservations.stream().map(MarketingFlashSaleReservation::getOrderId)
                .filter(Objects::nonNull).distinct().toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, TradeOrder> result = new HashMap<>();
        for (TradeOrder order : tradeOrderMapper.selectBatchIds(orderIds)) {
            result.put(order.getId(), order);
        }
        return result;
    }

    /**
     * 活动商品明细转详情行。
     *
     * @param item 活动商品明细
     * @param products 商品映射
     * @param skus SKU映射
     * @return 详情行
     * @author Henfon
     * @date 2026-09-17
     */
    private static MarketingFlashSaleItemRow toItemRow(MarketingFlashSaleItem item, Map<Long, CatalogProduct> products,
                                                      Map<Long, CatalogSku> skus) {
        CatalogProduct product = item.getProductId() == null ? null : products.get(item.getProductId());
        CatalogSku sku = item.getSkuId() == null ? null : skus.get(item.getSkuId());
        // 原价口径与活动保存校验一致：指定 SKU 取 SKU 价格，否则取商品价格。
        BigDecimal originalPrice = sku != null ? sku.getPrice() : product == null ? null : product.getPrice();
        int totalStock = item.getTotalStock() == null ? 0 : item.getTotalStock();
        int soldStock = item.getSoldStock() == null ? 0 : item.getSoldStock();
        return new MarketingFlashSaleItemRow(item.getId(), item.getProductId(),
                product == null ? null : product.getProductCode(),
                product == null ? null : product.getProductName(),
                item.getSkuId(), sku == null ? null : sku.getSkuCode(), sku == null ? null : sku.getSkuName(),
                item.getActivityPrice(), originalPrice, discountRate(item.getActivityPrice(), originalPrice),
                totalStock, soldStock, Math.max(totalStock - soldStock, 0), item.getLimitPerMember(),
                item.getStatus(), itemStatusText(item.getStatus()), item.getCreatedAt(), item.getUpdatedAt());
    }

    /**
     * 预占记录转详情行。
     *
     * @param reservation 预占记录
     * @param item 对应的活动明细，可能已随活动编辑被逻辑删除
     * @param products 商品映射
     * @param skus SKU映射
     * @param members 会员映射
     * @param orders 订单映射
     * @return 详情行
     * @author Henfon
     * @date 2026-09-17
     */
    private static MarketingFlashSaleReservationRow toReservationRow(MarketingFlashSaleReservation reservation,
                                                                    MarketingFlashSaleItem item,
                                                                    Map<Long, CatalogProduct> products,
                                                                    Map<Long, CatalogSku> skus,
                                                                    Map<Long, MemberUser> members,
                                                                    Map<Long, TradeOrder> orders) {
        MemberUser member = reservation.getMemberId() == null ? null : members.get(reservation.getMemberId());
        TradeOrder order = reservation.getOrderId() == null ? null : orders.get(reservation.getOrderId());
        CatalogProduct product = item == null || item.getProductId() == null ? null : products.get(item.getProductId());
        CatalogSku sku = item == null || item.getSkuId() == null ? null : skus.get(item.getSkuId());
        return new MarketingFlashSaleReservationRow(reservation.getId(), reservation.getCreatedAt(),
                reservation.getMemberId(), member == null ? null : member.getMemberNo(),
                memberDisplayName(member), member == null ? null : member.getPhone(),
                reservation.getOrderId(), order == null ? null : order.getOrderNo(),
                item == null ? null : item.getProductId(), product == null ? null : product.getProductName(),
                item == null ? null : item.getSkuId(), sku == null ? null : sku.getSkuName(),
                reservation.getQuantity(), reservation.getStatus(),
                reservationStatusText(reservation.getStatus()), reservation.getReleasedAt());
    }

    /**
     * 会员展示名称：优先昵称，回退用户名。
     *
     * @param member 会员
     * @return 展示名称，会员缺失时为空
     * @author Henfon
     * @date 2026-09-17
     */
    private static String memberDisplayName(MemberUser member) {
        if (member == null) {
            return null;
        }
        return StringUtils.hasText(member.getNickname()) ? member.getNickname() : member.getUsername();
    }

    /**
     * 活动配置状态结合排期时间推导展示状态。
     *
     * @param activity 活动
     * @return 展示状态文案
     * @author Henfon
     * @date 2026-09-17
     */
    private static String activityStatusText(MarketingFlashSale activity) {
        Integer status = activity.getStatus();
        if (status == null || status == 0) {
            return "草稿";
        }
        LocalDateTime now = LocalDateTime.now();
        // 调度器收口自然过期的活动时同样写 status=2，所以时间判断要排在状态判断之前，
        // 否则已结束的活动会被展示成运营手动停用。
        if (activity.getEndAt() != null && now.isAfter(activity.getEndAt())) {
            return "已结束";
        }
        if (status == 2) {
            return "已停用";
        }
        if (activity.getStartAt() != null && now.isBefore(activity.getStartAt())) {
            return "即将开始";
        }
        return "进行中";
    }

    /**
     * 活动商品启用状态文案。
     *
     * @param status 状态值
     * @return 状态文案
     * @author Henfon
     * @date 2026-09-17
     */
    private static String itemStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return status == 1 ? "启用" : "停用";
    }

    /**
     * 预占状态文案。
     *
     * @param status 状态值
     * @return 状态文案
     * @author Henfon
     * @date 2026-09-17
     */
    private static String reservationStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return status == 1 ? "已释放" : "预占中";
    }

    /**
     * 计算售罄率，单位为百分数，保留一位小数。
     *
     * @param soldStock 已售库存
     * @param totalStock 总库存
     * @return 售罄率，总库存为 0 时返回 0
     * @author Henfon
     * @date 2026-09-17
     */
    private static BigDecimal sellThroughRate(long soldStock, long totalStock) {
        if (totalStock <= 0L) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(soldStock).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalStock), 1, RoundingMode.HALF_UP);
    }

    /**
     * 计算活动价相对原价的折扣率，单位为百分数，保留一位小数。
     *
     * @param activityPrice 活动价
     * @param originalPrice 原价
     * @return 折扣率，原价缺失或非正数时为空
     * @author Henfon
     * @date 2026-09-17
     */
    private static BigDecimal discountRate(BigDecimal activityPrice, BigDecimal originalPrice) {
        if (activityPrice == null || originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return activityPrice.multiply(BigDecimal.valueOf(100))
                .divide(originalPrice, 1, RoundingMode.HALF_UP);
    }

    /**
     * 空值归零。
     *
     * @param value 待处理值
     * @return 非空值或 0
     * @author Henfon
     * @date 2026-09-17
     */
    private static long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 空值归零。
     *
     * @param value 待处理值
     * @return 非空值或 0
     * @author Henfon
     * @date 2026-09-17
     */
    private static int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
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
        // 明细是整体重写，门户缓存必须立即失效，否则门户还会按旧价格、旧库存展示。
        portalCache.evict();
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
        if (Integer.valueOf(1).equals(status)) {
            // 启用是对外承诺库存的时刻，按最新可用量复校一次，避免保存后被普通订单买空仍照常上线。
            validateActivityAvailableStock(activity);
        }
        activity.setStatus(status);
        if (activityMapper.updateById(activity) == 0) {
            throw new BusinessException("MARKETING_FLASH_SALE_CONCURRENT", "活动已被其他操作修改，请刷新后重试");
        }
        // 启停直接决定活动是否出现在门户，缓存必须立即失效。
        portalCache.evict();
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
        // 删除后门户不应再展示该活动，缓存立即失效。
        portalCache.evict();
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
        int availableStock = resolveAvailableStock(item.productId(), item.skuId());
        if (item.totalStock() - soldStock > availableStock) {
            throw new BusinessException("MARKETING_FLASH_SALE_STOCK_INVALID",
                    "活动库存不能超过商品当前可用库存（可用 " + availableStock + "）");
        }
    }

    /**
     * 解析活动明细对应的可用库存上限。
     *
     * <p>口径必须与下单扣减一致，取 inventory 台账的可用量：catalog 的 stock 字段是期初总库存，
     * 订单只改 inventory_stock，用它会随销量积累持续高估。明细未指定 SKU 时语义是该商品任意 SKU
     * 参与，而下单扣减针对请求里的具体 SKU，因此上限取商品下全部启用 SKU 的可用量之和。</p>
     *
     * @param productId 商品ID
     * @param skuId SKU ID，可为空
     * @return 可用库存
     * @author Henfon
     * @date 2026-09-17
     */
    private int resolveAvailableStock(Long productId, Long skuId) {
        if (skuId != null) {
            return inventoryStockService.availableStock(skuId);
        }
        List<CatalogSku> skus = skuMapper.selectList(new LambdaQueryWrapper<CatalogSku>()
                .eq(CatalogSku::getProductId, productId)
                .eq(CatalogSku::getStatus, 1));
        if (skus == null || skus.isEmpty()) {
            // 商品没有启用 SKU 时不存在可售单位，活动配额只能配 0。
            return 0;
        }
        return inventoryStockService.availableStockBySkuIds(skus.stream().map(CatalogSku::getId).toList())
                .values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * 校验活动剩余名额不超过明细对应商品的当前可用库存。
     *
     * <p>保存到启用之间库存可能被普通订单买走，而启用才是对外承诺库存的时刻，所以启用前再校一次。
     * 剩余量已为 0 的明细跳过，避免历史售罄明细卡住启用操作。</p>
     *
     * @param activity 活动
     * @author Henfon
     * @date 2026-09-17
     */
    private void validateActivityAvailableStock(MarketingFlashSale activity) {
        List<MarketingFlashSaleItem> items = itemMapper.selectList(new LambdaQueryWrapper<MarketingFlashSaleItem>()
                .eq(MarketingFlashSaleItem::getActivityId, activity.getId()));
        if (items == null) {
            return;
        }
        for (MarketingFlashSaleItem item : items) {
            int remaining = zeroIfNull(item.getTotalStock()) - zeroIfNull(item.getSoldStock());
            if (remaining <= 0) {
                continue;
            }
            int available = resolveAvailableStock(item.getProductId(), item.getSkuId());
            if (remaining > available) {
                throw new BusinessException("MARKETING_FLASH_SALE_STOCK_INVALID",
                        "商品 " + item.getProductId() + " 的活动剩余库存超过当前可用库存（可用 " + available + "）");
            }
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
