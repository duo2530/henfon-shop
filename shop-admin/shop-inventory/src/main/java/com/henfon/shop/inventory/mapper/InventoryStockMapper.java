package com.henfon.shop.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.inventory.entity.InventoryStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 库存台账数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Mapper
public interface InventoryStockMapper extends BaseMapper<InventoryStock> {

    /**
     * 原子锁定可用库存。
     *
     * @param stockId 台账ID
     * @param quantity 锁定数量
     * @return 受影响行数
     * @author Henfon
     * @date 2026-08-30
     */
    @Update("UPDATE inventory_stock SET available_stock = available_stock - #{quantity}, "
            + "locked_stock = locked_stock + #{quantity}, version = version + 1 "
            + "WHERE id = #{stockId} AND is_deleted = 0 AND available_stock >= #{quantity}")
    int reserve(@Param("stockId") Long stockId, @Param("quantity") int quantity);

    /**
     * 原子释放已锁定库存。
     *
     * @param stockId 台账ID
     * @param quantity 释放数量
     * @return 受影响行数
     * @author Henfon
     * @date 2026-08-30
     */
    @Update("UPDATE inventory_stock SET available_stock = available_stock + #{quantity}, "
            + "locked_stock = locked_stock - #{quantity}, version = version + 1 "
            + "WHERE id = #{stockId} AND is_deleted = 0 AND locked_stock >= #{quantity}")
    int release(@Param("stockId") Long stockId, @Param("quantity") int quantity);
}
