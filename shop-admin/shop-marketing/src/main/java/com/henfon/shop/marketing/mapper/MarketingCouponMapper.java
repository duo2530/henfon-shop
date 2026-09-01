package com.henfon.shop.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.marketing.entity.MarketingCoupon;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠券数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Mapper
public interface MarketingCouponMapper extends BaseMapper<MarketingCoupon> {

    /**
     * 在领取事务中锁定优惠券记录，串行化同一优惠券的领取配额计算。
     *
     * @param couponId 优惠券ID
     * @return 被锁定的优惠券
     * @author Henfon
     * @date 2026-09-01
     */
    @org.apache.ibatis.annotations.Select("SELECT * FROM marketing_coupon WHERE id = #{couponId} AND is_deleted = 0 LIMIT 1 FOR UPDATE")
    MarketingCoupon selectByIdForUpdate(@org.apache.ibatis.annotations.Param("couponId") Long couponId);

    /**
     * 原子增加优惠券领取数量。
     *
     * @param couponId 优惠券ID
     * @param now 当前时间
     * @return 更新行数
     * @author Henfon
     * @date 2026-08-30
     */
    @org.apache.ibatis.annotations.Update("UPDATE marketing_coupon SET claimed_quantity = claimed_quantity + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{couponId} AND status = 1 AND start_at <= #{now} AND end_at >= #{now} "
            + "AND (total_quantity = 0 OR claimed_quantity < total_quantity) AND is_deleted = 0")
    int incrementClaimed(@org.apache.ibatis.annotations.Param("couponId") Long couponId,
                         @org.apache.ibatis.annotations.Param("now") java.time.LocalDateTime now);

    /**
     * 原子减少优惠券领取数量。
     *
     * @param couponId 优惠券ID
     * @return 更新行数
     * @author Henfon
     * @date 2026-08-30
     */
    @org.apache.ibatis.annotations.Update("UPDATE marketing_coupon SET claimed_quantity = claimed_quantity - 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{couponId} AND claimed_quantity > 0 AND is_deleted = 0")
    int decrementClaimed(@org.apache.ibatis.annotations.Param("couponId") Long couponId);
}
