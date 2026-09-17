package com.henfon.shop.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.marketing.dto.MarketingFlashSaleReservationSummary;
import com.henfon.shop.marketing.entity.MarketingFlashSaleReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 秒杀库存预占记录数据访问接口。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Mapper
public interface MarketingFlashSaleReservationMapper extends BaseMapper<MarketingFlashSaleReservation> {

    /**
     * 汇总活动的预占情况。
     *
     * <p>预占记录随活动时长持续增长，参与人数、订单数与预占/释放数量都在数据库侧按条件聚合，
     * 页面只拿到一行结果。预占表没有逻辑删除列，无需额外过滤。</p>
     *
     * @param activityId 活动ID
     * @return 预占聚合结果，活动无人参与时计数为 0、时间为空
     * @author Henfon
     * @date 2026-09-17
     */
    @Select("SELECT COUNT(*) AS reservation_count, "
            + "COALESCE(SUM(CASE WHEN status = 0 THEN quantity ELSE 0 END), 0) AS reserved_quantity, "
            + "COALESCE(SUM(CASE WHEN status = 1 THEN quantity ELSE 0 END), 0) AS released_quantity, "
            + "COUNT(DISTINCT member_id) AS participant_count, "
            + "COUNT(DISTINCT order_id) AS order_count, "
            + "MIN(created_at) AS earliest_reserved_at, "
            + "MAX(created_at) AS latest_reserved_at "
            + "FROM marketing_flash_sale_reservation "
            + "WHERE activity_id = #{activityId}")
    MarketingFlashSaleReservationSummary summarize(@Param("activityId") Long activityId);
}
