package com.henfon.shop.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.inventory.entity.InventorySupplierStock;
import org.apache.ibatis.annotations.Mapper;

/** 供应商SKU供货关系数据访问接口。 @author Henfon @date 2026-09-04 */
@Mapper
public interface InventorySupplierStockMapper extends BaseMapper<InventorySupplierStock> {
}
