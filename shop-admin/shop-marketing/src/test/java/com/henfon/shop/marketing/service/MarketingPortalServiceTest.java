package com.henfon.shop.marketing.service;

import com.henfon.shop.catalog.entity.CatalogCategory;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.mapper.CatalogCategoryMapper;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.marketing.entity.MarketingCoupon;
import com.henfon.shop.marketing.entity.MarketingMemberCoupon;
import com.henfon.shop.marketing.entity.MarketingCouponUsage;
import com.henfon.shop.marketing.mapper.MarketingCouponMapper;
import com.henfon.shop.marketing.mapper.MarketingCouponUsageMapper;
import com.henfon.shop.marketing.mapper.MarketingMemberCouponMapper;
import com.henfon.shop.marketing.dto.MarketingCouponRedeemRequest;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.entity.TradeOrderItem;
import com.henfon.shop.trade.service.TradeOrderService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门户优惠券适用商品金额计算测试。
 *
 * @author Henfon
 * @date 2026-09-01
 */
class MarketingPortalServiceTest {

    private final MarketingCouponMapper couponMapper = mock(MarketingCouponMapper.class);
    private final MarketingMemberCouponMapper memberCouponMapper = mock(MarketingMemberCouponMapper.class);
    private final MarketingCouponUsageMapper usageMapper = mock(MarketingCouponUsageMapper.class);
    private final TradeOrderService tradeOrderService = mock(TradeOrderService.class);
    private final CatalogProductMapper catalogProductMapper = mock(CatalogProductMapper.class);
    private final CatalogCategoryMapper catalogCategoryMapper = mock(CatalogCategoryMapper.class);
    private final MarketingPortalService service = new MarketingPortalService(couponMapper, memberCouponMapper,
            usageMapper, tradeOrderService, catalogProductMapper, catalogCategoryMapper);

    /**
     * 验证门户优惠券查询走兼容历史数据库结构的显式字段 SQL。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldLoadActiveCouponsThroughCompatibilityQuery() {
        MarketingCoupon coupon = activeCategoryCoupon(11L, "DIGITAL");
        when(couponMapper.selectActiveCoupons(any(LocalDateTime.class))).thenReturn(List.of(coupon));

        // 调用门户查询，确保不会触发基于实体全字段生成的 selectList。
        List<MarketingCoupon> result = service.coupons();

        assertEquals(1, result.size());
        assertEquals(coupon, result.get(0));
        verify(couponMapper).selectActiveCoupons(any(LocalDateTime.class));
        verify(couponMapper, never()).selectList(any());
    }

    /**
     * 验证类目券只按命中类目的订单明细计算门槛和抵扣金额。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldApplyDiscountToApplicableCategorySubtotalOnly() {
        long memberId = 7L;
        long couponId = 11L;
        long orderId = 19L;
        MarketingCoupon coupon = activeCategoryCoupon(couponId, "DIGITAL");
        MarketingMemberCoupon memberCoupon = memberCoupon(couponId, memberId);
        TradeOrder order = pendingOrder(orderId, memberId, "200.00");
        TradeOrderItem matched = item(1L, "120.00");
        TradeOrderItem unmatched = item(2L, "80.00");
        CatalogProduct matchedProduct = product(101L);
        CatalogProduct unmatchedProduct = product(102L);
        CatalogCategory digital = category("DIGITAL");
        CatalogCategory furniture = category("FURNITURE");

        when(tradeOrderService.findById(orderId)).thenReturn(order);
        when(memberCouponMapper.selectOne(any())).thenReturn(memberCoupon);
        when(couponMapper.selectById(couponId)).thenReturn(coupon);
        when(tradeOrderService.listItems(orderId)).thenReturn(List.of(matched, unmatched));
        when(catalogProductMapper.selectById(1L)).thenReturn(matchedProduct);
        when(catalogProductMapper.selectById(2L)).thenReturn(unmatchedProduct);
        when(catalogCategoryMapper.selectById(101L)).thenReturn(digital);
        when(catalogCategoryMapper.selectById(102L)).thenReturn(furniture);
        when(memberCouponMapper.updateById(memberCoupon)).thenReturn(1);

        // 执行核销并验证抵扣金额只取命中类目的商品小计。
        service.redeem(memberId, new MarketingCouponRedeemRequest(couponId, orderId));

        // 仅命中类目的 120 元参与计算，50 元优惠不会受订单中其他类目商品影响。
        verify(tradeOrderService).applyCouponDiscount(orderId, new BigDecimal("50.00"));
    }

    /**
     * 验证类目券在订单没有命中商品时拒绝核销。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldRejectWhenNoApplicableCategoryItemExists() {
        long memberId = 7L;
        long couponId = 11L;
        long orderId = 19L;
        MarketingCoupon coupon = activeCategoryCoupon(couponId, "DIGITAL");
        TradeOrderItem unmatched = item(2L, "80.00");

        when(tradeOrderService.findById(orderId)).thenReturn(pendingOrder(orderId, memberId, "80.00"));
        when(memberCouponMapper.selectOne(any())).thenReturn(memberCoupon(couponId, memberId));
        when(couponMapper.selectById(couponId)).thenReturn(coupon);
        when(tradeOrderService.listItems(orderId)).thenReturn(List.of(unmatched));
        when(catalogProductMapper.selectById(2L)).thenReturn(product(102L));
        when(catalogCategoryMapper.selectById(102L)).thenReturn(category("FURNITURE"));

        // 无匹配类目时应在核销前直接拒绝，避免产生优惠流水。
        assertThrows(BusinessException.class,
                () -> service.redeem(memberId, new MarketingCouponRedeemRequest(couponId, orderId)));
    }

    /**
     * 验证会员达到优惠券领取上限后拒绝继续领取。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldRejectClaimWhenPerMemberLimitReached() {
        long memberId = 7L;
        long couponId = 11L;
        MarketingCoupon coupon = activeCategoryCoupon(couponId, "DIGITAL");
        coupon.setPerMemberLimit(1);

        // 模拟当前会员已经拥有一张该优惠券，但逻辑删除记录不应被重复计算由 Mapper 过滤。
        when(memberCouponMapper.selectOne(any())).thenReturn(null);
        when(couponMapper.selectByIdForUpdate(couponId)).thenReturn(coupon);
        when(memberCouponMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.claim(memberId, couponId));
        // 达到上限时应在库存递增前失败，避免错误消耗发行库存。
        verify(couponMapper, never()).incrementClaimed(org.mockito.ArgumentMatchers.eq(couponId), any());
    }

    /**
     * 验证单会员领取上限为两张时可以成功领取第二张。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldAllowSecondClaimWhenPerMemberLimitIsTwo() {
        long memberId = 7L;
        long couponId = 11L;
        MarketingCoupon coupon = activeCategoryCoupon(couponId, "DIGITAL");
        coupon.setPerMemberLimit(2);
        when(couponMapper.selectByIdForUpdate(couponId)).thenReturn(coupon);
        when(memberCouponMapper.selectCount(any())).thenReturn(1L);
        when(couponMapper.incrementClaimed(org.mockito.ArgumentMatchers.eq(couponId), any())).thenReturn(1);
        when(memberCouponMapper.insert(any(MarketingMemberCoupon.class))).thenReturn(1);

        MarketingMemberCoupon result = service.claim(memberId, couponId);

        assertEquals(couponId, result.getCouponId());
        assertEquals(memberId, result.getMemberId());
        verify(memberCouponMapper).insert(any(MarketingMemberCoupon.class));
    }

    /**
     * 验证重复核销同一订单时优先返回已使用的原优惠券，避免再次抵扣。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldReturnExistingRedeemedCouponForSameOrder() {
        long memberId = 7L;
        long couponId = 11L;
        long orderId = 19L;
        MarketingMemberCoupon redeemed = memberCoupon(couponId, memberId);
        redeemed.setReceiveStatus(1);
        redeemed.setOrderId(orderId);
        // 模拟当前订单已存在核销记录，重复请求不应再查询可用券或修改订单金额。
        when(tradeOrderService.findById(orderId)).thenReturn(pendingOrder(orderId, memberId, "100.00"));
        when(memberCouponMapper.selectOne(any())).thenReturn(redeemed);

        MarketingMemberCoupon result = service.redeem(memberId,
                new MarketingCouponRedeemRequest(couponId, orderId));

        assertEquals(redeemed, result);
        verify(tradeOrderService, never()).applyCouponDiscount(any(), any());
        verify(couponMapper, never()).selectById(any());
        verify(memberCouponMapper, never()).updateById(org.mockito.ArgumentMatchers.any(MarketingMemberCoupon.class));
    }

    /**
     * 验证订单已有其他优惠券核销记录时拒绝叠加使用，避免折扣金额和流水不一致。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectStackingDifferentCouponOnSameOrder() {
        long memberId = 7L;
        long orderId = 19L;
        long existingCouponId = 11L;
        long requestedCouponId = 12L;
        MarketingCouponUsage usage = new MarketingCouponUsage();
        usage.setCouponId(existingCouponId);
        usage.setMemberId(memberId);
        usage.setOrderId(orderId);
        usage.setAction(1);

        when(tradeOrderService.findById(orderId)).thenReturn(pendingOrder(orderId, memberId, "100.00"));
        when(usageMapper.selectOne(any())).thenReturn(usage);

        // 不应继续查询或修改会员优惠券，直接返回叠加不支持的业务错误。
        assertThrows(BusinessException.class,
                () -> service.redeem(memberId, new MarketingCouponRedeemRequest(requestedCouponId, orderId)));
        verify(memberCouponMapper, never()).selectOne(any());
        verify(tradeOrderService, never()).applyCouponDiscount(any(), any());
    }

    /**
     * 验证部分退款按退款占比计算优惠券分摊金额，并写入分摊流水。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldAllocateCouponDiscountForPartialRefund() {
        MarketingCouponUsage redeem = new MarketingCouponUsage();
        redeem.setCouponId(11L);
        redeem.setMemberCouponId(22L);
        redeem.setMemberId(7L);
        redeem.setOrderId(19L);
        redeem.setDiscountAmount(new BigDecimal("30.00"));
        redeem.setAction(1);
        when(usageMapper.selectOne(any())).thenReturn(redeem, null);

        BigDecimal allocated = service.allocatePartialRefund(7L, 19L,
                new BigDecimal("50.00"), new BigDecimal("100.00"));

        assertEquals(new BigDecimal("15.00"), allocated);
        verify(usageMapper).insert(any(MarketingCouponUsage.class));
    }

    /**
     * 验证重复部分退款通知不会重复插入流水，并返回已记录的分摊金额。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldKeepPartialRefundAllocationIdempotent() {
        MarketingCouponUsage redeem = new MarketingCouponUsage();
        redeem.setCouponId(11L);
        redeem.setMemberCouponId(22L);
        redeem.setMemberId(7L);
        redeem.setOrderId(19L);
        redeem.setDiscountAmount(new BigDecimal("30.00"));
        MarketingCouponUsage partial = new MarketingCouponUsage();
        partial.setDiscountAmount(new BigDecimal("15.00"));
        when(usageMapper.selectOne(any())).thenReturn(redeem, partial);

        BigDecimal allocated = service.allocatePartialRefund(7L, 19L,
                new BigDecimal("50.00"), new BigDecimal("100.00"));

        assertEquals(new BigDecimal("15.00"), allocated);
        verify(usageMapper, never()).insert(any(MarketingCouponUsage.class));
        verify(usageMapper, never()).updateById(any(MarketingCouponUsage.class));
    }

    /**
     * 创建一张有效的类目优惠券测试数据。
     *
     * @param id 优惠券ID
     * @param categoryCode 类目编码
     * @return 优惠券测试实体
     * @author Henfon
     * @date 2026-09-01
     */
    private MarketingCoupon activeCategoryCoupon(Long id, String categoryCode) {
        // 构造有效期覆盖当前时间的类目券。
        MarketingCoupon coupon = new MarketingCoupon();
        coupon.setId(id);
        coupon.setCategoryCode(categoryCode);
        coupon.setStatus(1);
        coupon.setMinSpend(new BigDecimal("100.00"));
        coupon.setDiscountAmount(new BigDecimal("50.00"));
        coupon.setStartAt(LocalDateTime.now().minusDays(1));
        coupon.setEndAt(LocalDateTime.now().plusDays(1));
        return coupon;
    }

    /**
     * 创建待支付订单测试数据。
     *
     * @param orderId 订单ID
     * @param memberId 会员ID
     * @param subtotal 商品小计
     * @return 订单测试实体
     * @author Henfon
     * @date 2026-09-01
     */
    private TradeOrder pendingOrder(Long orderId, Long memberId, String subtotal) {
        // 使用待支付、未核销状态，满足门户核销前置条件。
        TradeOrder order = new TradeOrder();
        order.setId(orderId);
        order.setMemberId(memberId);
        order.setOrderStatus(10);
        order.setPaymentStatus(0);
        order.setSubtotalAmount(new BigDecimal(subtotal));
        return order;
    }

    /**
     * 创建会员未使用优惠券测试数据。
     *
     * @param couponId 优惠券ID
     * @param memberId 会员ID
     * @return 会员优惠券测试实体
     * @author Henfon
     * @date 2026-09-01
     */
    private MarketingMemberCoupon memberCoupon(Long couponId, Long memberId) {
        // 会员券保持未使用状态，供 redeem 流程更新。
        MarketingMemberCoupon record = new MarketingMemberCoupon();
        record.setId(31L);
        record.setCouponId(couponId);
        record.setMemberId(memberId);
        record.setReceiveStatus(0);
        return record;
    }

    /**
     * 创建订单明细测试数据。
     *
     * @param productId 商品ID
     * @param amount 明细金额
     * @return 订单明细测试实体
     * @author Henfon
     * @date 2026-09-01
     */
    private TradeOrderItem item(Long productId, String amount) {
        // 明细金额直接填写，覆盖单价为空的历史订单兼容分支。
        TradeOrderItem item = new TradeOrderItem();
        item.setProductId(productId);
        item.setItemAmount(new BigDecimal(amount));
        return item;
    }

    /**
     * 创建商品测试数据。
     *
     * @param categoryId 类目ID
     * @return 商品测试实体
     * @author Henfon
     * @date 2026-09-01
     */
    private CatalogProduct product(Long categoryId) {
        // 商品仅需绑定类目 ID 即可驱动类目匹配。
        CatalogProduct product = new CatalogProduct();
        product.setCategoryId(categoryId);
        return product;
    }

    /**
     * 创建类目测试数据。
     *
     * @param code 类目编码
     * @return 类目测试实体
     * @author Henfon
     * @date 2026-09-01
     */
    private CatalogCategory category(String code) {
        // 类目编码用于与优惠券配置执行大小写不敏感匹配。
        CatalogCategory category = new CatalogCategory();
        category.setCategoryCode(code);
        return category;
    }
}
