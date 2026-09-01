package com.henfon.shop.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.marketing.entity.MarketingCoupon;
import com.henfon.shop.marketing.entity.MarketingMemberCoupon;
import com.henfon.shop.marketing.mapper.MarketingCouponMapper;
import com.henfon.shop.marketing.mapper.MarketingMemberCouponMapper;
import com.henfon.shop.marketing.mapper.MarketingCouponUsageMapper;
import com.henfon.shop.marketing.entity.MarketingCouponUsage;
import com.henfon.shop.marketing.dto.MarketingCouponRedeemRequest;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.entity.TradeOrderItem;
import com.henfon.shop.trade.service.TradeOrderService;
import com.henfon.shop.catalog.entity.CatalogCategory;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.mapper.CatalogCategoryMapper;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.math.BigDecimal;
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
    private final MarketingCouponUsageMapper usageMapper;
    private final TradeOrderService tradeOrderService;
    private final CatalogProductMapper catalogProductMapper;
    private final CatalogCategoryMapper catalogCategoryMapper;

    /**
     * 创建门户营销服务。
     *
     * @param couponMapper 优惠券数据访问对象
     * @param memberCouponMapper 会员优惠券数据访问对象
     * @param usageMapper 优惠券核销流水数据访问对象
     * @param tradeOrderService 交易订单应用服务
     * @param catalogProductMapper 商品数据访问对象
     * @param catalogCategoryMapper 类目数据访问对象
     * @author Henfon
     * @date 2026-08-29
     */
    public MarketingPortalService(MarketingCouponMapper couponMapper, MarketingMemberCouponMapper memberCouponMapper,
                                  MarketingCouponUsageMapper usageMapper, TradeOrderService tradeOrderService,
                                  CatalogProductMapper catalogProductMapper, CatalogCategoryMapper catalogCategoryMapper) {
        this.couponMapper = couponMapper;
        this.memberCouponMapper = memberCouponMapper;
        this.usageMapper = usageMapper;
        this.tradeOrderService = tradeOrderService;
        this.catalogProductMapper = catalogProductMapper;
        this.catalogCategoryMapper = catalogCategoryMapper;
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

    /**
     * 会员领取优惠券，使用数据库条件更新保护并发超发并保证重复领取幂等。
     *
     * @param memberId 会员ID
     * @param couponId 优惠券ID
     * @return 会员优惠券记录
     * @author Henfon
     * @date 2026-08-30
     */
    @org.springframework.transaction.annotation.Transactional
    public MarketingMemberCoupon claim(Long memberId, Long couponId) {
        MarketingMemberCoupon existed = memberCouponMapper.selectOne(new LambdaQueryWrapper<MarketingMemberCoupon>()
                .eq(MarketingMemberCoupon::getMemberId, memberId)
                .eq(MarketingMemberCoupon::getCouponId, couponId));
        if (existed != null) {
            return existed;
        }
        MarketingCoupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BusinessException("MARKETING_COUPON_NOT_FOUND", "优惠券不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStatus() == null || coupon.getStatus() != 1
                || coupon.getStartAt() == null || coupon.getEndAt() == null
                || now.isBefore(coupon.getStartAt()) || now.isAfter(coupon.getEndAt())) {
            throw new BusinessException("MARKETING_COUPON_INACTIVE", "优惠券当前不可领取");
        }
        int perMemberLimit = coupon.getPerMemberLimit() == null ? 1 : coupon.getPerMemberLimit();
        if (perMemberLimit < 1) {
            throw new BusinessException("MARKETING_COUPON_MEMBER_LIMIT_INVALID", "优惠券单会员领取上限配置无效");
        }
        // 领取记录按会员和优惠券维度统计，兼容历史数据中尚未回填上限字段的优惠券。
        long memberClaimedCount = memberCouponMapper.selectCount(new LambdaQueryWrapper<MarketingMemberCoupon>()
                .eq(MarketingMemberCoupon::getMemberId, memberId)
                .eq(MarketingMemberCoupon::getCouponId, couponId));
        if (memberClaimedCount >= perMemberLimit) {
            throw new BusinessException("MARKETING_COUPON_MEMBER_LIMIT_REACHED", "已达到该优惠券的单会员领取上限");
        }
        // 仅在库存充足时递增已领取数量，避免并发请求超发。
        if (couponMapper.incrementClaimed(couponId, now) == 0) {
            throw new BusinessException("MARKETING_COUPON_SOLD_OUT", "优惠券已领完");
        }
        MarketingMemberCoupon record = new MarketingMemberCoupon();
        record.setCouponId(couponId);
        record.setMemberId(memberId);
        record.setReceiveStatus(0);
        record.setReceivedAt(now);
        try {
            memberCouponMapper.insert(record);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            // 并发重复领取时回滚数量并返回已存在记录，保证接口幂等。
            couponMapper.decrementClaimed(couponId);
            MarketingMemberCoupon concurrent = memberCouponMapper.selectOne(new LambdaQueryWrapper<MarketingMemberCoupon>()
                    .eq(MarketingMemberCoupon::getMemberId, memberId)
                    .eq(MarketingMemberCoupon::getCouponId, couponId));
            if (concurrent != null) {
                return concurrent;
            }
            throw duplicate;
        }
        return record;
    }

    /**
     * 核销会员优惠券并记录流水，重复核销同一订单时直接返回成功。
     *
     * @param memberId 会员ID
     * @param request 核销请求
     * @return 核销后的会员优惠券
     * @author Henfon
     * @date 2026-08-30
     */
    @org.springframework.transaction.annotation.Transactional
    public MarketingMemberCoupon redeem(Long memberId, MarketingCouponRedeemRequest request) {
        // 先校验订单归属，禁止会员将自己的优惠券核销到其他会员订单。
        TradeOrder order = tradeOrderService.findById(request.orderId());
        if (order.getMemberId() == null || !order.getMemberId().equals(memberId)) {
            throw new BusinessException("MARKETING_COUPON_ORDER_FORBIDDEN", "无权为该订单核销优惠券");
        }
        MarketingMemberCoupon record = memberCouponMapper.selectOne(new LambdaQueryWrapper<MarketingMemberCoupon>()
                .eq(MarketingMemberCoupon::getMemberId, memberId)
                .eq(MarketingMemberCoupon::getCouponId, request.couponId()));
        if (record == null) {
            throw new BusinessException("MARKETING_MEMBER_COUPON_NOT_FOUND", "会员未领取该优惠券");
        }
        if (Integer.valueOf(1).equals(record.getReceiveStatus()) && request.orderId().equals(record.getOrderId())) {
            return record;
        }
        if (!Integer.valueOf(0).equals(record.getReceiveStatus())) {
            throw new BusinessException("MARKETING_COUPON_ALREADY_USED", "优惠券已使用或已失效");
        }
        MarketingCoupon coupon = couponMapper.selectById(record.getCouponId());
        LocalDateTime now = LocalDateTime.now();
        if (coupon == null || !Integer.valueOf(1).equals(coupon.getStatus())
                || coupon.getStartAt() == null || coupon.getEndAt() == null
                || now.isBefore(coupon.getStartAt()) || now.isAfter(coupon.getEndAt())) {
            throw new BusinessException("MARKETING_COUPON_INACTIVE", "优惠券当前不可使用");
        }
        if (!Integer.valueOf(10).equals(order.getOrderStatus()) || !Integer.valueOf(0).equals(order.getPaymentStatus())) {
            throw new BusinessException("MARKETING_COUPON_ORDER_STATUS_INVALID", "订单当前状态不允许使用优惠券");
        }
        BigDecimal subtotal = calculateApplicableSubtotal(coupon, request.orderId(), order);
        if (subtotal.compareTo(coupon.getMinSpend()) < 0) {
            throw new BusinessException("MARKETING_COUPON_MIN_SPEND", "优惠券适用商品金额未达到使用门槛");
        }
        BigDecimal configuredDiscount = coupon.getDiscountAmount() == null ? BigDecimal.ZERO : coupon.getDiscountAmount();
        if (configuredDiscount.signum() < 0) {
            throw new BusinessException("MARKETING_COUPON_DISCOUNT_INVALID", "优惠券优惠金额配置无效");
        }
        // 现金券最高只能抵扣商品金额，避免错误配置导致订单应付金额为负数。
        BigDecimal appliedDiscount = configuredDiscount.min(subtotal).setScale(2, java.math.RoundingMode.HALF_UP);
        tradeOrderService.applyCouponDiscount(order.getId(), appliedDiscount);
        record.setReceiveStatus(1);
        record.setUsedAt(now);
        record.setOrderId(request.orderId());
        if (memberCouponMapper.updateById(record) == 0) {
            throw new BusinessException("MARKETING_COUPON_CONCURRENT", "优惠券状态已变化，请刷新后重试");
        }
        MarketingCouponUsage usage = new MarketingCouponUsage();
        usage.setCouponId(record.getCouponId());
        usage.setMemberCouponId(record.getId());
        usage.setMemberId(memberId);
        usage.setOrderId(request.orderId());
        usage.setDiscountAmount(appliedDiscount);
        usage.setAction(1);
        usageMapper.insert(usage);
        return record;
    }

    /**
     * 计算优惠券可抵扣的商品金额，类目券只统计命中类目的订单明细。
     *
     * @param coupon 优惠券
     * @param orderId 订单ID
     * @param order 订单主表
     * @return 可参与门槛和抵扣计算的商品金额
     * @author Henfon
     * @date 2026-09-01
     */
    private BigDecimal calculateApplicableSubtotal(MarketingCoupon coupon, Long orderId, TradeOrder order) {
        BigDecimal orderSubtotal = order.getSubtotalAmount() == null ? BigDecimal.ZERO : order.getSubtotalAmount();
        String couponCategoryCode = coupon.getCategoryCode();
        if (!StringUtils.hasText(couponCategoryCode)) {
            return orderSubtotal;
        }
        List<TradeOrderItem> items = tradeOrderService.listItems(orderId);
        if (items == null || items.isEmpty()) {
            throw new BusinessException("MARKETING_COUPON_CATEGORY_MISMATCH", "订单商品不适用该优惠券");
        }
        BigDecimal applicableSubtotal = BigDecimal.ZERO;
        for (TradeOrderItem item : items) {
            if (item == null || item.getProductId() == null) {
                continue;
            }
            CatalogProduct product = catalogProductMapper.selectById(item.getProductId());
            CatalogCategory category = product == null || product.getCategoryId() == null
                    ? null : catalogCategoryMapper.selectById(product.getCategoryId());
            if (category != null && StringUtils.hasText(category.getCategoryCode())
                    && couponCategoryCode.trim().equalsIgnoreCase(category.getCategoryCode().trim())) {
                // 优先使用订单明细金额，兼容历史数据缺少 itemAmount 的场景再按单价乘数量计算。
                BigDecimal itemAmount = item.getItemAmount();
                if (itemAmount == null && item.getUnitPrice() != null && item.getQuantity() != null) {
                    itemAmount = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                }
                if (itemAmount != null && itemAmount.signum() > 0) {
                    applicableSubtotal = applicableSubtotal.add(itemAmount);
                }
            }
        }
        if (applicableSubtotal.signum() <= 0) {
            throw new BusinessException("MARKETING_COUPON_CATEGORY_MISMATCH", "订单商品不适用该优惠券");
        }
        return applicableSubtotal;
    }

    /**
     * 订单取消后回滚该会员订单使用的优惠券，重复请求保持幂等。
     *
     * @param memberId 会员ID
     * @param orderId 订单ID
     * @return 回滚后的会员优惠券，订单未使用优惠券时返回空
     * @author Henfon
     * @date 2026-08-30
     */
    @org.springframework.transaction.annotation.Transactional
    public MarketingMemberCoupon rollback(Long memberId, Long orderId) {
        MarketingCouponUsage usage = usageMapper.selectOne(new LambdaQueryWrapper<MarketingCouponUsage>()
                .eq(MarketingCouponUsage::getMemberId, memberId)
                .eq(MarketingCouponUsage::getOrderId, orderId)
                .eq(MarketingCouponUsage::getAction, 1)
                .last("LIMIT 1"));
        if (usage == null) {
            return null;
        }
        MarketingMemberCoupon record = memberCouponMapper.selectOne(new LambdaQueryWrapper<MarketingMemberCoupon>()
                .eq(MarketingMemberCoupon::getId, usage.getMemberCouponId())
                .eq(MarketingMemberCoupon::getMemberId, memberId)
                .last("LIMIT 1 FOR UPDATE"));
        if (record == null) {
            throw new BusinessException("MARKETING_MEMBER_COUPON_NOT_FOUND", "会员优惠券不存在");
        }
        MarketingCouponUsage rollback = usageMapper.selectOne(new LambdaQueryWrapper<MarketingCouponUsage>()
                .eq(MarketingCouponUsage::getMemberCouponId, record.getId())
                .eq(MarketingCouponUsage::getOrderId, orderId)
                .eq(MarketingCouponUsage::getAction, 2)
                .last("LIMIT 1"));
        if (rollback != null) {
            return record;
        }
        MarketingCoupon coupon = couponMapper.selectById(record.getCouponId());
        boolean expired = coupon == null || coupon.getEndAt() == null || LocalDateTime.now().isAfter(coupon.getEndAt());
        record.setReceiveStatus(expired ? 2 : 0);
        record.setUsedAt(null);
        record.setOrderId(null);
        if (memberCouponMapper.updateById(record) == 0) {
            throw new BusinessException("MARKETING_COUPON_CONCURRENT", "优惠券状态已变化，请刷新后重试");
        }
        MarketingCouponUsage rollbackUsage = new MarketingCouponUsage();
        rollbackUsage.setCouponId(record.getCouponId());
        rollbackUsage.setMemberCouponId(record.getId());
        rollbackUsage.setMemberId(memberId);
        rollbackUsage.setOrderId(orderId);
        rollbackUsage.setDiscountAmount(usage.getDiscountAmount());
        rollbackUsage.setAction(2);
        usageMapper.insert(rollbackUsage);
        return record;
    }
}
