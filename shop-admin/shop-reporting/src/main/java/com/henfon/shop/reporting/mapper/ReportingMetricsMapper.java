package com.henfon.shop.reporting.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.henfon.shop.reporting.dto.ReportingSalesTrendRow;
import com.henfon.shop.reporting.dto.ReportingProductRankingRow;
import com.henfon.shop.reporting.dto.ReportingMemberLevelStatRow;
import com.henfon.shop.reporting.dto.ReportingChannelStat;

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
     * 按支付渠道聚合成功支付统计。
     *
     * @param startTime 开始时间（包含）
     * @param endTime 结束时间（不包含）
     * @return 渠道统计原始结果
     * @author Henfon
     * @date 2026-09-04
     */
    @Select("""
            SELECT UPPER(TRIM(channel)) AS channel,
                   COUNT(*) AS payment_order_count,
                   COALESCE(SUM(amount), 0.00) AS paid_amount
            FROM payment_order
            WHERE is_deleted = 0
              AND status = 2
              AND paid_at >= #{startTime}
              AND paid_at < #{endTime}
            GROUP BY UPPER(TRIM(channel))
            ORDER BY paid_amount DESC, channel ASC
            """)
    List<ReportingChannelStat> listChannelStats(@Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    /**
     * 按销售额聚合商品排行。
     *
     * @param startTime 开始时间（包含）
     * @param endTime 结束时间（不包含）
     * @param limit 返回条数
     * @return 商品排行原始聚合结果
     * @author Henfon
     * @date 2026-09-01
     */
    @Select("""
            SELECT i.product_id AS product_id,
                   MAX(i.product_name) AS product_name,
                   MAX(COALESCE(p.category_name, '未分类')) AS category_name,
                   COALESCE(SUM(i.quantity), 0) AS sales_volume,
                   COALESCE(SUM(i.item_amount), 0.00) AS sales_amount,
                   COUNT(DISTINCT o.id) AS order_count
            FROM trade_order_item i
            INNER JOIN trade_order o ON o.id = i.order_id
            LEFT JOIN catalog_product p ON p.id = i.product_id AND p.is_deleted = 0
            WHERE i.is_deleted = 0
              AND o.is_deleted = 0
              AND o.payment_status = 1
              AND o.order_status <> 70
              AND o.paid_at >= #{startTime}
              AND o.paid_at < #{endTime}
            GROUP BY i.product_id
            ORDER BY sales_amount DESC, sales_volume DESC, i.product_id ASC
            LIMIT #{limit}
            """)
    List<ReportingProductRankingRow> listProductRanking(@Param("startTime") LocalDateTime startTime,
                                                        @Param("endTime") LocalDateTime endTime,
                                                        @Param("limit") int limit);

    /**
     * 按会员等级聚合会员消费分析。
     *
     * @param startTime 开始时间（包含）
     * @param endTime 结束时间（不包含）
     * @return 会员等级分析原始聚合结果
     * @author Henfon
     * @date 2026-09-01
     */
    @Select("""
            SELECT m.member_level AS member_level,
                   COUNT(DISTINCT m.id) AS member_count,
                   COUNT(DISTINCT CASE WHEN o.id IS NOT NULL THEN m.id END) AS active_member_count,
                   COUNT(o.id) AS paid_order_count,
                   COALESCE(SUM(o.paid_amount), 0.00) AS paid_amount
            FROM member_user m
            LEFT JOIN trade_order o ON o.member_id = m.id
                AND o.is_deleted = 0
                AND o.payment_status = 1
                AND o.order_status <> 70
                AND o.paid_at >= #{startTime}
                AND o.paid_at < #{endTime}
            WHERE m.is_deleted = 0
              AND m.registered_at < #{endTime}
            GROUP BY m.member_level
            ORDER BY paid_amount DESC, member_level ASC
            """)
    List<ReportingMemberLevelStatRow> listMemberLevelStats(@Param("startTime") LocalDateTime startTime,
                                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定范围内发生两笔及以上有效支付订单的会员数。
     *
     * @param startTime 开始时间（包含）
     * @param endTime 结束时间（不包含）
     * @return 复购会员数量
     * @author Henfon
     * @date 2026-09-01
     */
    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT member_id
                FROM trade_order
                WHERE is_deleted = 0
                  AND member_id IS NOT NULL
                  AND payment_status = 1
                  AND order_status <> 70
                  AND paid_at >= #{startTime}
                  AND paid_at < #{endTime}
                GROUP BY member_id
                HAVING COUNT(*) >= 2
            ) repeat_members
            """)
    Long countRepeatMembers(@Param("startTime") LocalDateTime startTime,
                            @Param("endTime") LocalDateTime endTime);

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
