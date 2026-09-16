package com.henfon.shop.export.dataset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.export.dto.ExportQuery;
import com.henfon.shop.export.entity.ExportType;
import com.henfon.shop.export.excel.ExcelColumn;
import com.henfon.shop.marketing.entity.MarketingCoupon;
import com.henfon.shop.marketing.entity.MarketingCouponUsage;
import com.henfon.shop.marketing.mapper.MarketingCouponMapper;
import com.henfon.shop.marketing.mapper.MarketingCouponUsageMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 优惠券报表导出数据集。
 *
 * <p>支持按券名称/券代码关键字、启停状态，以及页面上的有效期状态（进行中、未开始、已过期）收窄范围。
 * 有效期状态在页面上是按 start_at/end_at 与当前时间比较推导出来的，服务端没有对应的状态列，
 * 因此这里在 SQL 里还原同一套判据，保证导出结果与页面表格一致。</p>
 *
 * <p>页面的「卡券类型」（满减/折扣/包邮）没有下沉到这里：该分类取自 tag 这个自由文本列，
 * 页面把无法识别的取值一律归为满减券，若在导出侧重新实现一遍分类，报表与页面会出现对不上的口径。</p>
 *
 * @author Henfon
 * @date 2026-09-16
 */
@Component
public class CouponExportDataset extends AbstractExportDataset<CouponExportDataset.Row> {

    /** 核销流水标识，与 MarketingPortalService 中的 ACTION_REDEEM 一致。 */
    private static final int ACTION_REDEEM = 1;

    /** 券启用状态，与 MarketingCoupon.status 取值一致。 */
    private static final int STATUS_ENABLED = 1;

    /** 有效期状态取值，由页面按 start_at/end_at 推导后传入 statusText。 */
    private static final String VALIDITY_ACTIVE = "active";

    private static final String VALIDITY_SCHEDULED = "scheduled";

    private static final String VALIDITY_EXPIRED = "expired";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MarketingCouponMapper marketingCouponMapper;

    private final MarketingCouponUsageMapper marketingCouponUsageMapper;

    /**
     * 创建优惠券导出数据集。
     *
     * @param marketingCouponMapper 优惠券数据访问对象
     * @param marketingCouponUsageMapper 优惠券核销流水数据访问对象
     * @author Henfon
     * @date 2026-09-16
     */
    public CouponExportDataset(MarketingCouponMapper marketingCouponMapper,
                               MarketingCouponUsageMapper marketingCouponUsageMapper) {
        this.marketingCouponMapper = marketingCouponMapper;
        this.marketingCouponUsageMapper = marketingCouponUsageMapper;
    }

    /**
     * 优惠券导出行。
     *
     * @param id 券ID
     * @param name 券名称
     * @param code 券代码
     * @param type 类型
     * @param discountAmount 优惠额度
     * @param minSpend 门槛金额
     * @param totalQuantity 总发放量
     * @param perMemberLimit 单会员上限
     * @param claimedQuantity 已领取
     * @param usedQuantity 已核销
     * @param statusLabel 状态
     * @param validity 有效期
     * @author Henfon
     * @date 2026-09-16
     */
    public record Row(Long id, String name, String code, String type, BigDecimal discountAmount,
                      BigDecimal minSpend, Integer totalQuantity, Integer perMemberLimit,
                      Integer claimedQuantity, Long usedQuantity, String statusLabel, String validity) {
    }

    @Override
    public ExportType type() {
        return ExportType.COUPON;
    }

    @Override
    public List<ExcelColumn<Row>> columns() {
        return List.of(
                ExcelColumn.integer("优惠券ID", Row::id, 12),
                ExcelColumn.text("券名称", Row::name, 26),
                ExcelColumn.text("券代码", Row::code, 18),
                ExcelColumn.text("类型", Row::type, 10),
                ExcelColumn.money("优惠额度(¥)", Row::discountAmount, 14),
                ExcelColumn.money("门槛金额(¥)", Row::minSpend, 14),
                ExcelColumn.integer("总发放量", Row::totalQuantity, 12),
                ExcelColumn.integer("单会员上限", Row::perMemberLimit, 13),
                ExcelColumn.integer("已领取", Row::claimedQuantity, 10),
                ExcelColumn.integer("已核销", Row::usedQuantity, 10),
                ExcelColumn.text("状态", Row::statusLabel, 10),
                ExcelColumn.text("有效期", Row::validity, 36));
    }

    @Override
    public void stream(Consumer<Row> consumer, ExportQuery query) {
        LambdaQueryWrapper<MarketingCoupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.status() != null, MarketingCoupon::getStatus, query.status());
        applyValidityFilter(wrapper, query.statusText());
        if (StringUtils.hasText(query.keyword())) {
            String keyword = query.keyword().trim();
            wrapper.and(condition -> condition.like(MarketingCoupon::getCouponTitle, keyword)
                    .or().like(MarketingCoupon::getCouponCode, keyword));
        }
        wrapper.orderByDesc(MarketingCoupon::getId);

        Map<Long, Long> usedCounts = loadUsedCounts();
        streamPages(consumer, (pageNo, pageSize) -> {
            Page<MarketingCoupon> page = marketingCouponMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
            return page.getRecords().stream()
                    .map(coupon -> toRow(coupon, usedCounts.getOrDefault(coupon.getId(), 0L)))
                    .toList();
        });
    }

    /**
     * 按页面上推导的有效期状态收窄范围。
     *
     * <p>进行中与已过期/未开始都以「券处于启用状态」为前提，停用券不参与这三种状态；
     * 判据里的时间只取一次，避免分页循环跨过时间点导致前后两页口径漂移。</p>
     *
     * @param wrapper 查询条件
     * @param statusText 有效期状态，取值 active/scheduled/expired
     * @author Henfon
     * @date 2026-09-16
     */
    private void applyValidityFilter(LambdaQueryWrapper<MarketingCoupon> wrapper, String statusText) {
        if (!StringUtils.hasText(statusText)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        switch (statusText.trim().toLowerCase(Locale.ROOT)) {
            case VALIDITY_ACTIVE -> {
                wrapper.eq(MarketingCoupon::getStatus, STATUS_ENABLED);
                wrapper.and(condition -> condition.isNull(MarketingCoupon::getStartAt)
                        .or().le(MarketingCoupon::getStartAt, now));
                wrapper.and(condition -> condition.isNull(MarketingCoupon::getEndAt)
                        .or().ge(MarketingCoupon::getEndAt, now));
            }
            case VALIDITY_SCHEDULED -> {
                wrapper.eq(MarketingCoupon::getStatus, STATUS_ENABLED);
                wrapper.gt(MarketingCoupon::getStartAt, now);
            }
            case VALIDITY_EXPIRED -> {
                wrapper.eq(MarketingCoupon::getStatus, STATUS_ENABLED);
                wrapper.lt(MarketingCoupon::getEndAt, now);
            }
            default -> {
                // 其他取值（如财务对账状态）不属于优惠券维度，不参与过滤。
            }
        }
    }

    /**
     * 统计每张券的核销次数。
     *
     * @return 券ID到核销次数的映射
     * @author Henfon
     * @date 2026-09-16
     */
    private Map<Long, Long> loadUsedCounts() {
        // 只取核销流水的券ID列，避免把整张流水表的明细读进内存。
        List<MarketingCouponUsage> usages = marketingCouponUsageMapper.selectList(
                new LambdaQueryWrapper<MarketingCouponUsage>()
                        .select(MarketingCouponUsage::getCouponId)
                        .eq(MarketingCouponUsage::getAction, ACTION_REDEEM));
        Map<Long, Long> counts = new HashMap<>();
        for (MarketingCouponUsage usage : usages) {
            if (usage.getCouponId() != null) {
                counts.merge(usage.getCouponId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    /**
     * 实体转导出行为。
     *
     * @param coupon 优惠券实体
     * @param usedCount 核销次数
     * @return 导出行
     * @author Henfon
     * @date 2026-09-16
     */
    private static Row toRow(MarketingCoupon coupon, Long usedCount) {
        return new Row(coupon.getId(), coupon.getCouponTitle(), coupon.getCouponCode(),
                coupon.getTag(), coupon.getDiscountAmount(), coupon.getMinSpend(),
                coupon.getTotalQuantity(), coupon.getPerMemberLimit(), coupon.getClaimedQuantity(),
                usedCount, coupon.getStatus() != null && coupon.getStatus() == 1 ? "启用中" : "已停用",
                formatValidity(coupon.getStartAt(), coupon.getEndAt()));
    }

    /**
     * 格式化有效期区间。
     *
     * @param startAt 开始时间
     * @param endAt 结束时间
     * @return 有效期文案
     * @author Henfon
     * @date 2026-09-16
     */
    private static String formatValidity(LocalDateTime startAt, LocalDateTime endAt) {
        String start = startAt == null ? "不限" : startAt.format(TIME_FORMATTER);
        String end = endAt == null ? "不限" : endAt.format(TIME_FORMATTER);
        return start + " ~ " + end;
    }
}
