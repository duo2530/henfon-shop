package com.henfon.shop.reporting.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.henfon.shop.reporting.dto.ReportingSalesTrendRow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台经营指标聚合数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Mapper
public interface ReportingMetricsMapper {

    /**
     * 按自然日聚合指定范围内的销售趋势。
     *
     * @param startTime 开始时间（包含）
     * @param endTime 结束时间（不包含）
     * @return 每日销售趋势原始聚合结果
     * @author Henfon
     * @date 2026-08-31
     */
    @Select("""
            SELECT DATE(o.paid_at) AS date,
                   COALESCE(SUM(o.paid_amount), 0.00) AS sales_amount,
                   COUNT(*) AS order_count,
                   COALESCE(SUM(item.product_quantity), 0) AS product_quantity
            FROM trade_order o
            LEFT JOIN (
                SELECT order_id, SUM(quantity) AS product_quantity
                FROM trade_order_item
                WHERE is_deleted = 0
                GROUP BY order_id
            ) item ON item.order_id = o.id
            WHERE o.is_deleted = 0
              AND o.payment_status = 1
              AND o.order_status <> 70
              AND o.paid_at >= #{startTime}
              AND o.paid_at < #{endTime}
            GROUP BY DATE(o.paid_at)
            ORDER BY date
            """)
    List<ReportingSalesTrendRow> listSalesTrend(@Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

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
