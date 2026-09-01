package com.henfon.shop.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.marketing.dto.MarketingFlashSalePortalResponse;
import com.henfon.shop.marketing.entity.MarketingFlashSale;
import com.henfon.shop.marketing.entity.MarketingFlashSaleItem;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleItemMapper;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 门户秒杀活动查询服务。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
public class MarketingFlashSalePortalService {

    private final MarketingFlashSaleMapper activityMapper;
    private final MarketingFlashSaleItemMapper itemMapper;

    /**
     * 创建门户秒杀查询服务。
     *
     * @param activityMapper 秒杀活动数据访问对象
     * @param itemMapper 秒杀活动商品数据访问对象
     * @author Henfon
     * @date 2026-09-01
     */
    public MarketingFlashSalePortalService(MarketingFlashSaleMapper activityMapper,
                                           MarketingFlashSaleItemMapper itemMapper) {
        this.activityMapper = activityMapper;
        this.itemMapper = itemMapper;
    }

    /**
     * 查询当前时间窗口内仍有可售商品的秒杀活动。
     *
     * @return 门户秒杀活动列表
     * @author Henfon
     * @date 2026-09-01
     */
    public List<MarketingFlashSalePortalResponse> activeFlashSales() {
        LocalDateTime now = LocalDateTime.now();
        List<MarketingFlashSale> activities = activityMapper.selectList(new LambdaQueryWrapper<MarketingFlashSale>()
                .eq(MarketingFlashSale::getStatus, 1)
                .le(MarketingFlashSale::getStartAt, now)
                .ge(MarketingFlashSale::getEndAt, now)
                .orderByAsc(MarketingFlashSale::getStartAt)
                .orderByAsc(MarketingFlashSale::getId));
        if (activities == null || activities.isEmpty()) {
            return Collections.emptyList();
        }

        // 一次性加载所有活动商品，避免门户首页按活动逐个查询造成 N+1 请求。
        List<Long> activityIds = activities.stream()
                .map(MarketingFlashSale::getId)
                .filter(Objects::nonNull)
                .toList();
        if (activityIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<MarketingFlashSaleItem> items = itemMapper.selectList(new LambdaQueryWrapper<MarketingFlashSaleItem>()
                .in(MarketingFlashSaleItem::getActivityId, activityIds)
                .eq(MarketingFlashSaleItem::getStatus, 1)
                .apply("sold_stock < total_stock")
                .orderByAsc(MarketingFlashSaleItem::getId));
        Map<Long, List<MarketingFlashSaleItem>> itemsByActivity = (items == null ? Collections.<MarketingFlashSaleItem>emptyList() : items)
                .stream()
                .filter(item -> item.getActivityId() != null)
                .collect(Collectors.groupingBy(MarketingFlashSaleItem::getActivityId,
                        LinkedHashMap::new, Collectors.toList()));

        List<MarketingFlashSalePortalResponse> result = new ArrayList<>();
        for (MarketingFlashSale activity : activities) {
            List<MarketingFlashSaleItem> activityItems = itemsByActivity.getOrDefault(activity.getId(), Collections.emptyList());
            if (activityItems.isEmpty()) {
                // 活动虽然在时间窗口内，但商品售罄时不对门户展示。
                continue;
            }
            List<MarketingFlashSalePortalResponse.Item> responseItems = activityItems.stream()
                    .map(this::toResponseItem)
                    .toList();
            result.add(new MarketingFlashSalePortalResponse(activity.getId(), activity.getActivityCode(),
                    activity.getActivityName(), activity.getStartAt(), activity.getEndAt(),
                    activity.getLimitPerMember(), responseItems));
        }
        return result;
    }

    /**
     * 将活动商品实体转换为门户库存摘要。
     *
     * @param item 活动商品实体
     * @return 门户活动商品
     * @author Henfon
     * @date 2026-09-01
     */
    private MarketingFlashSalePortalResponse.Item toResponseItem(MarketingFlashSaleItem item) {
        int totalStock = item.getTotalStock() == null ? 0 : item.getTotalStock();
        int soldStock = item.getSoldStock() == null ? 0 : item.getSoldStock();
        // 数据库约束保证已售不超过总库存，额外兜底避免异常历史数据向前端返回负库存。
        int remainingStock = Math.max(totalStock - soldStock, 0);
        return new MarketingFlashSalePortalResponse.Item(item.getId(), item.getProductId(), item.getSkuId(),
                item.getActivityPrice(), totalStock, soldStock, remainingStock, item.getLimitPerMember());
    }
}
