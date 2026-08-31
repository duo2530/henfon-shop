package com.henfon.shop.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.marketing.dto.MarketingFlashSaleSaveRequest;
import com.henfon.shop.marketing.entity.MarketingFlashSale;
import com.henfon.shop.marketing.entity.MarketingFlashSaleItem;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleItemMapper;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 秒杀促销活动管理服务。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class MarketingFlashSaleService {

    private final MarketingFlashSaleMapper activityMapper;
    private final MarketingFlashSaleItemMapper itemMapper;

    /**
     * 创建秒杀活动服务。
     *
     * @param activityMapper 活动数据访问对象
     * @param itemMapper 活动商品数据访问对象
     * @author Henfon
     * @date 2026-08-31
     */
    public MarketingFlashSaleService(MarketingFlashSaleMapper activityMapper, MarketingFlashSaleItemMapper itemMapper) {
        this.activityMapper = activityMapper;
        this.itemMapper = itemMapper;
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
            item.setSoldStock(0);
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
