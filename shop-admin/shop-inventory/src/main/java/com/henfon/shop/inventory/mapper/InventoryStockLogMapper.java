package com.henfon.shop.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.inventory.entity.InventoryStockLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存变更流水数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Mapper
public interface InventoryStockLogMapper extends BaseMapper<InventoryStockLog> {
}
