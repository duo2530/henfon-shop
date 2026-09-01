package com.henfon.shop.marketing.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.marketing.dto.MarketingCouponSaveRequest;
import com.henfon.shop.marketing.entity.MarketingCoupon;
import com.henfon.shop.marketing.mapper.MarketingCouponMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台优惠券领取上限校验测试。
 *
 * @author Henfon
 * @date 2026-09-01
 */
class MarketingCouponAdminServiceTest {

    private final MarketingCouponMapper couponMapper = mock(MarketingCouponMapper.class);
    private final MarketingCouponAdminService service = new MarketingCouponAdminService(couponMapper);

    /**
     * 验证单会员领取上限不能小于 1。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldRejectInvalidPerMemberLimit() {
        // 使用零值覆盖服务层边界校验，确保绕过 Bean Validation 时仍不会落库。
        MarketingCouponSaveRequest request = request(100, 0);

        assertThrows(BusinessException.class, () -> service.save(request));
    }

    /**
     * 验证单会员领取上限不能超过有限发行总量。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldRejectPerMemberLimitExceedingTotalQuantity() {
        // 发行总量为 10 时，单会员最多领取 11 张属于无效配置。
        MarketingCouponSaveRequest request = request(10, 11);

        assertThrows(BusinessException.class, () -> service.save(request));
    }

    /**
     * 验证保存优惠券时持久化单会员领取上限字段。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldPersistPerMemberLimit() {
        when(couponMapper.insert(org.mockito.ArgumentMatchers.any(MarketingCoupon.class))).thenReturn(1);
        MarketingCouponSaveRequest request = request(100, 3);

        service.save(request);

        // 捕获实际写入实体，确认新增字段不会被服务层丢失。
        ArgumentCaptor<MarketingCoupon> captor = ArgumentCaptor.forClass(MarketingCoupon.class);
        verify(couponMapper).insert(captor.capture());
        assertEquals(3, captor.getValue().getPerMemberLimit());
    }

    /**
     * 构造后台优惠券保存请求。
     *
     * @param totalQuantity 发行总量
     * @param perMemberLimit 单会员领取上限
     * @return 保存请求
     * @author Henfon
     * @date 2026-09-01
     */
    private MarketingCouponSaveRequest request(int totalQuantity, int perMemberLimit) {
        // 使用固定有效期与金额，令测试只关注领取上限规则。
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        return new MarketingCouponSaveRequest(null, "LIMIT-TEST", "领取上限测试券",
                new BigDecimal("10.00"), new BigDecimal("50.00"), null, "cash", "测试",
                totalQuantity, perMemberLimit, start, end, 1);
    }
}
