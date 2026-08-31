package com.henfon.shop.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.inventory.entity.InventoryStocktake;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存盘点单数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Mapper
public interface InventoryStocktakeMapper extends BaseMapper<InventoryStocktake> {
}
