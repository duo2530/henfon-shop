package com.henfon.shop.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.inventory.entity.InventoryPurchaseItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购明细数据访问接口。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@Mapper
public interface InventoryPurchaseItemMapper extends BaseMapper<InventoryPurchaseItem> {
}
