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

    /**
     * 原子扣减已锁定库存并累计销量。
     *
     * @param stockId 台账ID
     * @param quantity 扣减数量
     * @return 受影响行数
     * @author Henfon
     * @date 2026-08-30
     */
    @Update("UPDATE inventory_stock SET locked_stock = locked_stock - #{quantity}, "
            + "sold_stock = sold_stock + #{quantity}, version = version + 1 "
            + "WHERE id = #{stockId} AND is_deleted = 0 AND locked_stock >= #{quantity}")
    int deduct(@Param("stockId") Long stockId, @Param("quantity") int quantity);

    /**
     * 按盘点差异原子调整可用库存并校验乐观锁版本。
     *
     * @param stockId 台账ID
     * @param differenceQuantity 盘盈盘亏数量
     * @param version 当前版本
     * @return 受影响行数
     * @author Henfon
     * @date 2026-08-31
     */
    @Update("UPDATE inventory_stock SET available_stock = available_stock + #{differenceQuantity}, "
            + "version = version + 1 WHERE id = #{stockId} AND is_deleted = 0 "
            + "AND version = #{version} AND available_stock + #{differenceQuantity} >= 0")
    int adjustByStocktake(@Param("stockId") Long stockId,
                          @Param("differenceQuantity") int differenceQuantity,
                          @Param("version") Integer version);
}
