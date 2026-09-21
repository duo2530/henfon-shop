package com.henfon.shop.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.marketing.entity.MarketingCoupon;
import com.henfon.shop.marketing.entity.MarketingMemberCoupon;
import com.henfon.shop.marketing.mapper.MarketingCouponMapper;
import com.henfon.shop.marketing.mapper.MarketingMemberCouponMapper;
import com.henfon.shop.marketing.mapper.MarketingCouponUsageMapper;
import com.henfon.shop.marketing.entity.MarketingCouponUsage;
import com.henfon.shop.marketing.dto.MarketingCouponRedeemRequest;
import com.henfon.shop.marketing.dto.MarketingMemberCouponView;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 门户营销查询服务。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class MarketingPortalService {
    /** 部分退款优惠券分摊流水动作。 */
    private static final int ACTION_REDEEM = 1;
    private static final int ACTION_ROLLBACK = 2;
    private static final int ACTION_PARTIAL_REFUND = 3;
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
        // 通过显式字段查询兼容未执行领取上限迁移的历史开发库，避免 MyBatis 自动拼接不存在的列。
        return couponMapper.selectActiveCoupons(now);
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
     * 查询会员已领取的优惠券，并补上券名与面额。
     *
     * 会员券表不存券名与金额，调用方（门户券包、AI 客服）都需要展示这些字段，
     * 在这里一次补齐，避免每个调用方各写一遍回表逻辑。券主表记录缺失时对应字段留空，
     * 不抛异常：券被删除不该让「我的券包」整页打不开。
     *
     * @param memberId 会员ID
     * @param status 领取状态，可为空
     * @return 带券面额信息的会员优惠券列表
     * @author Henfon
     * @date 2026-09-21
     */
    public List<MarketingMemberCouponView> memberCouponViews(Long memberId, Integer status) {
        List<MarketingMemberCoupon> records = memberCoupons(memberId, status);
        if (records.isEmpty()) {
            return List.of();
        }
        List<Long> couponIds = records.stream()
                .map(MarketingMemberCoupon::getCouponId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, MarketingCoupon> coupons = couponIds.isEmpty()
                ? Map.of()
                : couponMapper.selectBatchIds(couponIds).stream()
                        .collect(Collectors.toMap(MarketingCoupon::getId, coupon -> coupon, (left, right) -> left));
        return records.stream().map(record -> {
            MarketingCoupon coupon = coupons.get(record.getCouponId());
            return new MarketingMemberCouponView(
                    record.getCouponId(),
                    coupon == null ? null : coupon.getCouponTitle(),
                    coupon == null ? null : coupon.getDiscountAmount(),
                    coupon == null ? null : coupon.getMinSpend(),
                    coupon == null ? null : coupon.getStartAt(),
                    coupon == null ? null : coupon.getEndAt(),
                    record.getReceiveStatus(),
                    record.getReceivedAt());
        }).toList();
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
        // 先锁定优惠券主记录，再统计会员领取数量，避免并发请求突破单会员上限。
        MarketingCoupon coupon = couponMapper.selectByIdForUpdate(couponId);
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
        memberCouponMapper.insert(record);
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
        // 查询当前订单已经核销的优惠券，叠加规则按最多两张且累计优惠不超过商品小计控制。
        List<MarketingCouponUsage> existingUsages = usageMapper.selectList(new LambdaQueryWrapper<MarketingCouponUsage>()
                .eq(MarketingCouponUsage::getMemberId, memberId)
                .eq(MarketingCouponUsage::getOrderId, request.orderId())
                .eq(MarketingCouponUsage::getAction, ACTION_REDEEM));
        if (existingUsages == null || existingUsages.isEmpty()) {
            existingUsages = new java.util.ArrayList<>();
            // 兼容旧版数据访问实现，并读取单条历史核销记录。
            MarketingCouponUsage legacyUsage = usageMapper.selectOne(new LambdaQueryWrapper<MarketingCouponUsage>()
                    .eq(MarketingCouponUsage::getMemberId, memberId)
                    .eq(MarketingCouponUsage::getOrderId, request.orderId())
                    .eq(MarketingCouponUsage::getAction, ACTION_REDEEM)
                    .last("LIMIT 1"));
            if (legacyUsage != null) {
                existingUsages.add(legacyUsage);
            }
        }
        // 同一优惠券重复请求直接走幂等分支，不重复占用优惠券库存。
        MarketingCouponUsage existingUsage = existingUsages.stream()
                .filter(item -> request.couponId().equals(item.getCouponId()))
                .findFirst().orElse(null);
        if (existingUsage == null && !existingUsages.isEmpty()) {
            // 默认仍保持单券规则，仅当新旧优惠券均显式标记 STACKABLE 时允许叠加。
            MarketingCoupon requestedCoupon = couponMapper.selectById(request.couponId());
            MarketingCoupon previousCoupon = couponMapper.selectById(existingUsages.get(0).getCouponId());
            if (!isStackableCoupon(requestedCoupon) || !isStackableCoupon(previousCoupon)) {
                throw new BusinessException("MARKETING_COUPON_STACK_NOT_SUPPORTED", "当前优惠券不支持与其他优惠券叠加");
            }
        }
        if (existingUsages.size() >= 2 && existingUsage == null) {
            throw new BusinessException("MARKETING_COUPON_STACK_LIMIT", "同一订单最多叠加使用两张优惠券");
        }
        // 多张同券场景必须先查询当前订单已核销记录，避免重复请求误用另一张未使用优惠券。
        MarketingMemberCoupon record = memberCouponMapper.selectOne(new LambdaQueryWrapper<MarketingMemberCoupon>()
                .eq(MarketingMemberCoupon::getMemberId, memberId)
                .eq(MarketingMemberCoupon::getCouponId, request.couponId())
                .eq(MarketingMemberCoupon::getReceiveStatus, 1)
                .eq(MarketingMemberCoupon::getOrderId, request.orderId())
                .last("LIMIT 1"));
        if (record != null && Integer.valueOf(1).equals(record.getReceiveStatus())
                && request.orderId().equals(record.getOrderId())) {
            return record;
        }
        // 当前订单没有核销记录时，再按领取时间选择最早的可用优惠券。
        record = memberCouponMapper.selectOne(new LambdaQueryWrapper<MarketingMemberCoupon>()
                .eq(MarketingMemberCoupon::getMemberId, memberId)
                .eq(MarketingMemberCoupon::getCouponId, request.couponId())
                .eq(MarketingMemberCoupon::getReceiveStatus, 0)
                .orderByAsc(MarketingMemberCoupon::getReceivedAt)
                .last("LIMIT 1"));
        if (record == null) {
            throw new BusinessException("MARKETING_MEMBER_COUPON_NOT_FOUND", "会员未领取可用的该优惠券");
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
        BigDecimal existingDiscount = existingUsages.stream()
                .map(MarketingCouponUsage::getDiscountAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cumulativeDiscount = existingDiscount.add(appliedDiscount)
                .min(order.getSubtotalAmount() == null ? BigDecimal.ZERO : order.getSubtotalAmount())
                .setScale(2, java.math.RoundingMode.HALF_UP);
        // 仅将本次新增优惠应用到订单，累计金额由订单服务统一重算。
        BigDecimal incrementalDiscount = cumulativeDiscount.subtract(existingDiscount).max(BigDecimal.ZERO);
        if (incrementalDiscount.signum() > 0) {
            tradeOrderService.applyCouponDiscount(order.getId(), cumulativeDiscount);
        }
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
        usage.setAction(ACTION_REDEEM);
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
     * 判断优惠券是否允许参与叠加。
     *
     * @param coupon 优惠券
     * @return 是否允许叠加
     * @author Henfon
     * @date 2026-09-04
     */
    private boolean isStackableCoupon(MarketingCoupon coupon) {
        // 使用现有 tag 字段承载轻量配置，避免立即扩展数据库结构。
        return coupon != null && StringUtils.hasText(coupon.getTag())
                && java.util.Arrays.stream(coupon.getTag().split(","))
                .map(String::trim)
                .anyMatch("STACKABLE"::equalsIgnoreCase);
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
        List<MarketingCouponUsage> usages = usageMapper.selectList(new LambdaQueryWrapper<MarketingCouponUsage>()
                .eq(MarketingCouponUsage::getMemberId, memberId)
                .eq(MarketingCouponUsage::getOrderId, orderId)
                .eq(MarketingCouponUsage::getAction, ACTION_REDEEM));
        if (usages == null || usages.isEmpty()) {
            // 兼容旧版 Mapper 实现，降级读取单条核销记录。
            MarketingCouponUsage legacy = usageMapper.selectOne(new LambdaQueryWrapper<MarketingCouponUsage>()
                    .eq(MarketingCouponUsage::getMemberId, memberId)
                    .eq(MarketingCouponUsage::getOrderId, orderId)
                    .eq(MarketingCouponUsage::getAction, ACTION_REDEEM)
                    .last("LIMIT 1"));
            if (legacy != null) {
                usages = new java.util.ArrayList<>();
                usages.add(legacy);
            }
        }
        if (usages == null || usages.isEmpty()) {
            return null;
        }
        MarketingMemberCoupon firstRecord = null;
        for (MarketingCouponUsage usage : usages) {
            MarketingMemberCoupon record = memberCouponMapper.selectOne(new LambdaQueryWrapper<MarketingMemberCoupon>()
                    .eq(MarketingMemberCoupon::getId, usage.getMemberCouponId())
                    .eq(MarketingMemberCoupon::getMemberId, memberId)
                    .last("LIMIT 1 FOR UPDATE"));
            if (record == null) {
                throw new BusinessException("MARKETING_MEMBER_COUPON_NOT_FOUND", "会员优惠券不存在");
            }
            if (firstRecord == null) {
                firstRecord = record;
            }
            MarketingCouponUsage rollback = usageMapper.selectOne(new LambdaQueryWrapper<MarketingCouponUsage>()
                    .eq(MarketingCouponUsage::getMemberCouponId, record.getId())
                    .eq(MarketingCouponUsage::getOrderId, orderId)
                    .eq(MarketingCouponUsage::getAction, ACTION_ROLLBACK)
                    .last("LIMIT 1"));
            if (rollback != null) {
                continue;
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
            rollbackUsage.setAction(ACTION_ROLLBACK);
            usageMapper.insert(rollbackUsage);
        }
        return firstRecord;
    }

    /**
     * 按部分退款金额分摊优惠券优惠额，重复通知保持幂等且不提前返还优惠券。
     *
     * @param memberId 会员ID
     * @param orderId 订单ID
     * @param refundAmount 本次累计退款金额
     * @param paidAmount 订单实付金额
     * @return 本次累计分摊的优惠金额
     * @author Henfon
     * @date 2026-09-04
     */
    @org.springframework.transaction.annotation.Transactional
    public BigDecimal allocatePartialRefund(Long memberId, Long orderId,
                                            BigDecimal refundAmount, BigDecimal paidAmount) {
        if (memberId == null || orderId == null || refundAmount == null || paidAmount == null
                || refundAmount.signum() <= 0 || paidAmount.signum() <= 0
                || refundAmount.compareTo(paidAmount) > 0) {
            throw new BusinessException("MARKETING_REFUND_AMOUNT_INVALID", "部分退款金额参数无效");
        }
        List<MarketingCouponUsage> redeems = usageMapper.selectList(new LambdaQueryWrapper<MarketingCouponUsage>()
                .eq(MarketingCouponUsage::getMemberId, memberId)
                .eq(MarketingCouponUsage::getOrderId, orderId)
                .eq(MarketingCouponUsage::getAction, ACTION_REDEEM));
        if (redeems == null || redeems.isEmpty()) {
            // 兼容旧版数据访问实现，至少读取一条历史核销流水。
            MarketingCouponUsage legacy = usageMapper.selectOne(new LambdaQueryWrapper<MarketingCouponUsage>()
                    .eq(MarketingCouponUsage::getMemberId, memberId)
                    .eq(MarketingCouponUsage::getOrderId, orderId)
                    .eq(MarketingCouponUsage::getAction, ACTION_REDEEM)
                    .last("LIMIT 1"));
            if (legacy != null) {
                redeems = new java.util.ArrayList<>();
                redeems.add(legacy);
            }
        }
        if (redeems == null || redeems.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal totalDiscount = redeems.stream().map(MarketingCouponUsage::getDiscountAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, java.math.RoundingMode.HALF_UP);
        if (totalDiscount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal targetTotal = totalDiscount.multiply(refundAmount)
                .divide(paidAmount, 2, java.math.RoundingMode.HALF_UP).min(totalDiscount);
        BigDecimal allocatedTotal = BigDecimal.ZERO.setScale(2);
        for (int i = 0; i < redeems.size(); i++) {
            MarketingCouponUsage redeem = redeems.get(i);
            BigDecimal source = redeem.getDiscountAmount() == null ? BigDecimal.ZERO : redeem.getDiscountAmount();
            BigDecimal target = i == redeems.size() - 1
                    ? targetTotal.subtract(allocatedTotal).max(BigDecimal.ZERO)
                    : targetTotal.multiply(source).divide(totalDiscount, 2, java.math.RoundingMode.HALF_UP);
            MarketingCouponUsage partial = usageMapper.selectOne(new LambdaQueryWrapper<MarketingCouponUsage>()
                    .eq(MarketingCouponUsage::getMemberCouponId, redeem.getMemberCouponId())
                    .eq(MarketingCouponUsage::getOrderId, orderId)
                    .eq(MarketingCouponUsage::getAction, ACTION_PARTIAL_REFUND)
                    .last("LIMIT 1"));
            if (partial == null) {
                if (target.signum() > 0) {
                    MarketingCouponUsage created = new MarketingCouponUsage();
                    created.setCouponId(redeem.getCouponId());
                    created.setMemberCouponId(redeem.getMemberCouponId());
                    created.setMemberId(memberId);
                    created.setOrderId(orderId);
                    created.setDiscountAmount(target);
                    created.setAction(ACTION_PARTIAL_REFUND);
                    usageMapper.insert(created);
                }
                allocatedTotal = allocatedTotal.add(target);
            } else {
                BigDecimal previous = partial.getDiscountAmount() == null ? BigDecimal.ZERO : partial.getDiscountAmount();
                BigDecimal effective = target.max(previous);
                if (effective.compareTo(previous) > 0) {
                    partial.setDiscountAmount(effective);
                    if (usageMapper.updateById(partial) == 0) {
                        throw new BusinessException("MARKETING_REFUND_CONCURRENT", "退款分摊记录已被其他操作修改");
                    }
                }
                allocatedTotal = allocatedTotal.add(effective);
            }
        }
        return allocatedTotal.min(totalDiscount).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
