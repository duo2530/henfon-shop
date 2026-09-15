package com.henfon.shop.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.identity.dto.LogisticsCarrierResponse;
import com.henfon.shop.identity.entity.SysDictionaryItem;
import com.henfon.shop.identity.mapper.SysDictionaryItemMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;

/**
 * 系统字典查询服务。
 *
 * @author Henfon
 * @date 2026-09-15
 */
@Service
public class SystemDictionaryService {

    private static final String LOGISTICS_CARRIER_TYPE = "LOGISTICS_CARRIER";

    private final SysDictionaryItemMapper dictionaryItemMapper;

    /**
     * 创建系统字典服务。
     *
     * @param dictionaryItemMapper 字典项数据访问对象
     * @author Henfon
     * @date 2026-09-15
     */
    public SystemDictionaryService(SysDictionaryItemMapper dictionaryItemMapper) {
        this.dictionaryItemMapper = dictionaryItemMapper;
    }

    /**
     * 查询启用的物流承运商。
     *
     * @param tenantId 租户ID
     * @return 承运商字典列表
     * @author Henfon
     * @date 2026-09-15
     */
    public List<LogisticsCarrierResponse> listLogisticsCarriers(Long tenantId) {
        // 租户专属字典优先，平台字典作为所有租户的公共候选项。
        List<SysDictionaryItem> items = dictionaryItemMapper.selectList(new LambdaQueryWrapper<SysDictionaryItem>()
                .eq(SysDictionaryItem::getDictType, LOGISTICS_CARRIER_TYPE)
                .eq(SysDictionaryItem::getStatus, 1)
                .and(wrapper -> wrapper.eq(SysDictionaryItem::getTenantId, tenantId == null ? 0L : tenantId)
                        .or().eq(SysDictionaryItem::getTenantId, 0L))
                .orderByAsc(SysDictionaryItem::getSortNo)
                .orderByAsc(SysDictionaryItem::getId));
        // 按租户字典优先的顺序去重，避免租户自定义项与平台项重复展示。
        LinkedHashMap<String, LogisticsCarrierResponse> uniqueItems = new LinkedHashMap<>();
        items.stream()
                .sorted((left, right) -> Long.compare(right.getTenantId(), left.getTenantId()))
                .forEach(item -> uniqueItems.putIfAbsent(item.getItemCode(),
                        new LogisticsCarrierResponse(item.getItemCode(), item.getItemName(), item.getSortNo())));
        return List.copyOf(uniqueItems.values());
    }
}
