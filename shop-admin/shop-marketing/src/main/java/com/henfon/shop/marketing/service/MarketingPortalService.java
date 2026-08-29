package com.henfon.shop.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.marketing.entity.MarketingCoupon;
import com.henfon.shop.marketing.entity.MarketingMemberCoupon;
import com.henfon.shop.marketing.mapper.MarketingCouponMapper;
import com.henfon.shop.marketing.mapper.MarketingMemberCouponMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门户营销查询服务。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class MarketingPortalService {
    private final MarketingCouponMapper couponMapper;
    private final MarketingMemberCouponMapper memberCouponMapper;

    /**
     * 创建门户营销服务。
     *
     * @param couponMapper 优惠券数据访问对象
     * @param memberCouponMapper 会员优惠券数据访问对象
     * @author Henfon
     * @date 2026-08-29
     */
    public MarketingPortalService(MarketingCouponMapper couponMapper, MarketingMemberCouponMapper memberCouponMapper) {
        this.couponMapper = couponMapper;
        this.memberCouponMapper = memberCouponMapper;
    }

    /**
     * 查询当前有效优惠券。
     *
     * @return 优惠券列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<MarketingCoupon> coupons() {
        LocalDateTime now = LocalDateTime.now();
        return couponMapper.selectList(new LambdaQueryWrapper<MarketingCoupon>()
                .eq(MarketingCoupon::getStatus, 1)
                .le(MarketingCoupon::getStartAt, now)
                .ge(MarketingCoupon::getEndAt, now)
                .apply("(claimed_quantity < total_quantity OR total_quantity = 0)")
                .orderByDesc(MarketingCoupon::getDiscountAmount));
    }

    /**
     * 查询会员优惠券。
     *
     * @param memberId 会员ID
     * @param status 领取状态，可为空
     * @return 会员优惠券列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<MarketingMemberCoupon> memberCoupons(Long memberId, Integer status) {
        return memberCouponMapper.selectList(new LambdaQueryWrapper<MarketingMemberCoupon>()
                .eq(MarketingMemberCoupon::getMemberId, memberId)
                .eq(status != null, MarketingMemberCoupon::getReceiveStatus, status)
                .orderByDesc(MarketingMemberCoupon::getReceivedAt));
    }
}
