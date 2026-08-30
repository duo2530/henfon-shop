package com.henfon.shop.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.inventory.entity.InventoryStockLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 库存锁定流水数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Mapper
public interface InventoryStockLockMapper extends BaseMapper<InventoryStockLock> {

    /**
     * 原子将过期锁定流水标记为已释放，避免并发任务重复释放库存。
     *
     * @param lockId 锁定流水ID
     * @param releasedAt 释放时间
     * @return 更新行数
     * @author Henfon
     * @date 2026-08-30
     */
    @Update("UPDATE inventory_stock_lock SET status = 1, released_at = #{releasedAt}, "
            + "updated_at = CURRENT_TIMESTAMP(3), version = version + 1 "
            + "WHERE id = #{lockId} AND status = 0 AND is_deleted = 0")
    int markReleasedIfLocked(@Param("lockId") Long lockId, @Param("releasedAt") java.time.LocalDateTime releasedAt);
}
