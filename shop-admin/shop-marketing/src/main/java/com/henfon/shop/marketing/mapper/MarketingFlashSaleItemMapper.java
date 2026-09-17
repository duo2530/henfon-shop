package com.henfon.shop.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.marketing.dto.MarketingFlashSaleStockSummary;
import com.henfon.shop.marketing.entity.MarketingFlashSaleItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 秒杀活动商品数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Mapper
public interface MarketingFlashSaleItemMapper extends BaseMapper<MarketingFlashSaleItem> {

    /**
     * 汇总活动的商品数量与库存。
     *
     * <p>手写聚合走 {@code activity_id} 索引，明细条数再多也只回一行；逻辑删除条件必须显式带上，
     * 注解 SQL 不会被 MyBatis-Plus 的 {@code @TableLogic} 自动追加。</p>
     *
     * @param activityId 活动ID
     * @return 库存聚合结果，活动无商品时各项为 0
     * @author Henfon
     * @date 2026-09-17
     */
    @Select("SELECT COUNT(*) AS item_count, "
            + "COALESCE(SUM(total_stock), 0) AS total_stock, "
            + "COALESCE(SUM(sold_stock), 0) AS sold_stock "
            + "FROM marketing_flash_sale_item "
            + "WHERE activity_id = #{activityId} AND is_deleted = 0")
    MarketingFlashSaleStockSummary summarizeStock(@Param("activityId") Long activityId);
}
