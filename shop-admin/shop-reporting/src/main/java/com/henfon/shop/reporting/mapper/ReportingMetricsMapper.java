package com.henfon.shop.reporting.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台经营指标聚合数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Mapper
public interface ReportingMetricsMapper {

    /**
     * 统计指定时间范围内的订单数量。
     *
     * @param startTime 开始时间（包含）
     * @param endTime 结束时间（不包含）
     * @return 订单数量
     * @author Henfon
     * @date 2026-08-31
     */
    @Select("""
            SELECT COUNT(*)
            FROM trade_order
            WHERE is_deleted = 0
              AND created_at >= #{startTime}
              AND created_at < #{endTime}
            """)
    Long countOrders(@Param("startTime") LocalDateTime startTime,
                     @Param("endTime") LocalDateTime endTime);

    /**
     * 统计截至指定时间前创建的订单数量。
     *
     * @param endTime 截止时间（不包含）
     * @return 订单数量
     * @author Henfon
     * @date 2026-08-31
     */
    @Select("""
            SELECT COUNT(*)
            FROM trade_order
            WHERE is_deleted = 0
              AND created_at < #{endTime}
            """)
    Long countOrdersBefore(@Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定时间范围内的有效销售额。
     *
     * @param startTime 开始时间（包含）
     * @param endTime 结束时间（不包含）
     * @return 有效销售额
     * @author Henfon
     * @date 2026-08-31
     */
    @Select("""
            SELECT COALESCE(SUM(paid_amount), 0.00)
            FROM trade_order
            WHERE is_deleted = 0
              AND payment_status = 1
              AND order_status <> 70
              AND paid_at >= #{startTime}
              AND paid_at < #{endTime}
            """)
    BigDecimal sumSales(@Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);

    /**
     * 统计截至指定时间前的有效销售额。
     *
     * @param endTime 截止时间（不包含）
     * @return 有效销售额
     * @author Henfon
     * @date 2026-08-31
     */
    @Select("""
            SELECT COALESCE(SUM(paid_amount), 0.00)
            FROM trade_order
            WHERE is_deleted = 0
              AND payment_status = 1
              AND order_status <> 70
              AND paid_at < #{endTime}
            """)
    BigDecimal sumSalesBefore(@Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定时间范围内创建的商品数量。
     *
     * @param startTime 开始时间（包含）
     * @param endTime 结束时间（不包含）
     * @return 商品数量
     * @author Henfon
     * @date 2026-08-31
     */
    @Select("""
            SELECT COUNT(*)
            FROM catalog_product
            WHERE is_deleted = 0
              AND created_at >= #{startTime}
              AND created_at < #{endTime}
            """)
    Long countProducts(@Param("startTime") LocalDateTime startTime,
                       @Param("endTime") LocalDateTime endTime);

    /**
     * 统计截至指定时间前创建的商品数量。
     *
     * @param endTime 截止时间（不包含）
     * @return 商品数量
     * @author Henfon
     * @date 2026-08-31
     */
    @Select("""
            SELECT COUNT(*)
            FROM catalog_product
            WHERE is_deleted = 0
              AND created_at < #{endTime}
            """)
    Long countProductsBefore(@Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定时间范围内注册的会员数量。
     *
     * @param startTime 开始时间（包含）
     * @param endTime 结束时间（不包含）
     * @return 会员数量
     * @author Henfon
     * @date 2026-08-31
     */
    @Select("""
            SELECT COUNT(*)
            FROM member_user
            WHERE is_deleted = 0
              AND registered_at >= #{startTime}
              AND registered_at < #{endTime}
            """)
    Long countMembers(@Param("startTime") LocalDateTime startTime,
                      @Param("endTime") LocalDateTime endTime);

    /**
     * 统计截至指定时间前注册的会员数量。
     *
     * @param endTime 截止时间（不包含）
     * @return 会员数量
     * @author Henfon
     * @date 2026-08-31
     */
    @Select("""
            SELECT COUNT(*)
            FROM member_user
            WHERE is_deleted = 0
              AND registered_at < #{endTime}
            """)
    Long countMembersBefore(@Param("endTime") LocalDateTime endTime);
}
