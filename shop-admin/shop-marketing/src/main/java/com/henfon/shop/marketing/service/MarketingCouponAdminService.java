package com.henfon.shop.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.marketing.dto.MarketingCouponSaveRequest;
import com.henfon.shop.marketing.entity.MarketingCoupon;
import com.henfon.shop.marketing.mapper.MarketingCouponMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 后台优惠券管理应用服务。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Service
public class MarketingCouponAdminService {

    private final MarketingCouponMapper couponMapper;

    /**
     * 创建后台优惠券管理服务。
     *
     * @param couponMapper 优惠券数据访问对象
     * @author Henfon
     * @date 2026-08-30
     */
    public MarketingCouponAdminService(MarketingCouponMapper couponMapper) {
        this.couponMapper = couponMapper;
    }

    /**
     * 分页查询后台优惠券。
     *
     * @param keyword 券码或标题关键字
     * @param status 启停状态
     * @param current 当前页
     * @param size 页大小
     * @return 优惠券分页结果
     * @author Henfon
     * @date 2026-08-30
     */
    public IPage<MarketingCoupon> page(String keyword, Integer status, long current, long size) {
        // 限制分页大小，避免运营查询拖慢营销表。
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        LambdaQueryWrapper<MarketingCoupon> wrapper = new LambdaQueryWrapper<MarketingCoupon>()
                .eq(status != null, MarketingCoupon::getStatus, status)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(MarketingCoupon::getCouponCode, keyword)
                        .or().like(MarketingCoupon::getCouponTitle, keyword))
                .orderByDesc(MarketingCoupon::getCreatedAt);
        return couponMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
    }

    /**
     * 保存或更新优惠券。
     *
     * @param request 保存请求
     * @return 优惠券ID
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public Long save(MarketingCouponSaveRequest request) {
        validate(request);
        MarketingCoupon coupon = new MarketingCoupon();
        coupon.setId(request.id());
        coupon.setCouponCode(request.couponCode().trim());
        coupon.setCouponTitle(request.couponTitle().trim());
        coupon.setDiscountAmount(request.discountAmount());
        coupon.setMinSpend(request.minSpend());
        coupon.setCategoryCode(trimToNull(request.categoryCode()));
        coupon.setTag(trimToNull(request.tag()));
        coupon.setDescription(trimToNull(request.description()));
        coupon.setTotalQuantity(request.totalQuantity());
        coupon.setPerMemberLimit(request.perMemberLimit());
        coupon.setStartAt(request.startAt());
        coupon.setEndAt(request.endAt());
        coupon.setStatus(request.status());
        try {
            if (coupon.getId() == null) {
                coupon.setClaimedQuantity(0);
                couponMapper.insert(coupon);
            } else {
                MarketingCoupon existing = requireCoupon(coupon.getId());
                if (request.totalQuantity() < existing.getClaimedQuantity()) {
                    throw new BusinessException("MARKETING_COUPON_QUANTITY_INVALID", "发行总量不能小于已领取数量");
                }
                // 已有领取记录继续沿用当前配置，管理员只能把单会员上限调高到合法范围。
                if (request.perMemberLimit() == null || request.perMemberLimit() < 1) {
                    throw new BusinessException("MARKETING_COUPON_MEMBER_LIMIT_INVALID", "单会员领取上限必须大于 0");
                }
                coupon.setClaimedQuantity(existing.getClaimedQuantity());
                // 带上查询到的版本号，确保后台编辑不会覆盖并发领取产生的最新数据。
                coupon.setVersion(existing.getVersion());
                if (couponMapper.updateById(coupon) == 0) {
                    throw new BusinessException("MARKETING_COUPON_CONCURRENT", "优惠券已被其他操作修改，请刷新后重试");
                }
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("MARKETING_COUPON_CODE_EXISTS", "优惠券编码已存在");
        }
        return coupon.getId();
    }

    /**
     * 修改优惠券启停状态。
     *
     * @param id 优惠券ID
     * @param status 目标状态
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("MARKETING_COUPON_STATUS_INVALID", "优惠券状态必须为 0 或 1");
        }
        MarketingCoupon coupon = requireCoupon(id);
        coupon.setStatus(status);
        if (couponMapper.updateById(coupon) == 0) {
            throw new BusinessException("MARKETING_COUPON_CONCURRENT", "优惠券已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 逻辑删除优惠券。
     *
     * @param id 优惠券ID
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void delete(Long id) {
        requireCoupon(id);
        couponMapper.deleteById(id);
    }

    /**
     * 校验优惠券字段和有效期。
     *
     * @param request 保存请求
     * @author Henfon
     * @date 2026-08-30
     */
    private void validate(MarketingCouponSaveRequest request) {
        if (request.totalQuantity() < 0) {
            throw new BusinessException("MARKETING_COUPON_QUANTITY_INVALID", "发行总量不能为负数");
        }
        if (request.perMemberLimit() == null || request.perMemberLimit() < 1 || request.perMemberLimit() > 999999) {
            throw new BusinessException("MARKETING_COUPON_MEMBER_LIMIT_INVALID", "单会员领取上限必须在 1 到 999999 之间");
        }
        if (request.totalQuantity() > 0 && request.perMemberLimit() > request.totalQuantity()) {
            throw new BusinessException("MARKETING_COUPON_MEMBER_LIMIT_INVALID", "单会员领取上限不能超过发行总量");
        }
        if (request.startAt().isAfter(request.endAt())) {
            throw new BusinessException("MARKETING_COUPON_TIME_INVALID", "优惠券开始时间不能晚于结束时间");
        }
        if (request.discountAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("MARKETING_COUPON_AMOUNT_INVALID", "优惠金额必须大于 0");
        }
        if (request.minSpend().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("MARKETING_COUPON_AMOUNT_INVALID", "使用门槛不能为负数");
        }
        if (request.status() != 0 && request.status() != 1) {
            throw new BusinessException("MARKETING_COUPON_STATUS_INVALID", "优惠券状态必须为 0 或 1");
        }
    }

    /**
     * 查询优惠券，不存在时抛出统一业务异常。
     *
     * @param id 优惠券ID
     * @return 优惠券实体
     * @author Henfon
     * @date 2026-08-30
     */
    private MarketingCoupon requireCoupon(Long id) {
        if (id == null) {
            throw new BusinessException("MARKETING_COUPON_NOT_FOUND", "优惠券不存在");
        }
        MarketingCoupon coupon = couponMapper.selectById(id);
        if (coupon == null) {
            throw new BusinessException("MARKETING_COUPON_NOT_FOUND", "优惠券不存在");
        }
        return coupon;
    }

    /**
     * 清理可选文本。
     *
     * @param value 原始文本
     * @return 清理后的文本或空值
     * @author Henfon
     * @date 2026-08-30
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
