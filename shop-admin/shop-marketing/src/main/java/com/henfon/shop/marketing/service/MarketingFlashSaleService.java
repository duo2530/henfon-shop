package com.henfon.shop.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.common.marketing.FlashSaleReservationItem;
import com.henfon.shop.common.marketing.FlashSaleReservationService;
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

/**
 * 秒杀促销活动管理服务。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class MarketingFlashSaleService implements FlashSaleReservationService {

    private final MarketingFlashSaleMapper activityMapper;
    private final MarketingFlashSaleItemMapper itemMapper;
    private final MarketingFlashSaleReservationMapper reservationMapper;

    /**
     * 创建秒杀活动服务。
     *
     * @param activityMapper 活动数据访问对象
     * @param itemMapper 活动商品数据访问对象
     * @author Henfon
     * @date 2026-08-31
     */
    public MarketingFlashSaleService(MarketingFlashSaleMapper activityMapper, MarketingFlashSaleItemMapper itemMapper,
                                     MarketingFlashSaleReservationMapper reservationMapper) {
        this.activityMapper = activityMapper;
        this.itemMapper = itemMapper;
        this.reservationMapper = reservationMapper;
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
            MarketingFlashSaleItem item = itemMapper.selectOne(new LambdaQueryWrapper<MarketingFlashSaleItem>()
                    .eq(MarketingFlashSaleItem::getActivityId, activityId)
                    .eq(MarketingFlashSaleItem::getProductId, requestItem.productId())
                    .eq(requestItem.skuId() != null, MarketingFlashSaleItem::getSkuId, requestItem.skuId())
                    .isNull(requestItem.skuId() == null, MarketingFlashSaleItem::getSkuId)
                    .eq(MarketingFlashSaleItem::getStatus, 1)
                    .last("LIMIT 1 FOR UPDATE"));
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
