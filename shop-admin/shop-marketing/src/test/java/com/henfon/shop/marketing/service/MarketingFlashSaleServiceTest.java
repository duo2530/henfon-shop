package com.henfon.shop.marketing.service;

import com.henfon.shop.marketing.entity.MarketingFlashSaleReservation;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleItemMapper;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleMapper;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleReservationMapper;
import com.henfon.shop.common.marketing.FlashSaleReservationItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 秒杀库存预占服务测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
class MarketingFlashSaleServiceTest {

    private final MarketingFlashSaleMapper activityMapper = mock(MarketingFlashSaleMapper.class);
    private final MarketingFlashSaleItemMapper itemMapper = mock(MarketingFlashSaleItemMapper.class);
    private final MarketingFlashSaleReservationMapper reservationMapper = mock(MarketingFlashSaleReservationMapper.class);
    private final MarketingFlashSaleService service = new MarketingFlashSaleService(activityMapper, itemMapper,
            reservationMapper);

    /**
     * 验证同一订单重复预占时直接幂等返回，不重复扣减库存。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldReturnWhenOrderAlreadyReserved() {
        MarketingFlashSaleReservation reservation = new MarketingFlashSaleReservation();
        reservation.setActivityId(10L);
        reservation.setMemberId(20L);
        reservation.setOrderId(30L);
        reservation.setStatus(0);
        when(reservationMapper.selectList(any())).thenReturn(List.of(reservation));

        // 重试请求命中已有预占后直接结束，活动和商品表都不应再访问。
        service.reserve(10L, 20L, 30L, List.of(new FlashSaleReservationItem(100L, null, 1,
                java.math.BigDecimal.TEN)));

        verify(activityMapper, never()).selectById(any());
        verify(itemMapper, never()).update(any(), any());
        verify(reservationMapper, never()).insert(org.mockito.ArgumentMatchers.<MarketingFlashSaleReservation>any());
    }

    /**
     * 验证订单已绑定其他会员或活动时拒绝复用预占记录。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectOrderReservationConflict() {
        MarketingFlashSaleReservation reservation = new MarketingFlashSaleReservation();
        reservation.setActivityId(99L);
        reservation.setMemberId(88L);
        reservation.setOrderId(30L);
        reservation.setStatus(0);
        when(reservationMapper.selectList(any())).thenReturn(List.of(reservation));

        // 订单与当前请求上下文不一致时必须报错，防止跨会员篡改库存预占。
        assertThrows(RuntimeException.class, () -> service.reserve(10L, 20L, 30L,
                List.of(new FlashSaleReservationItem(100L, null, 1, java.math.BigDecimal.TEN))));
        verify(activityMapper, never()).selectById(any());
    }

    /**
     * 验证释放不存在的秒杀预占时安全返回。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldIgnoreReleaseWhenNoReservationExists() {
        when(reservationMapper.selectList(any())).thenReturn(List.of());

        // 普通订单或重复取消场景没有秒杀记录时不应更新库存。
        service.release(30L);

        verify(itemMapper, never()).update(any(), any());
        verify(reservationMapper, never()).updateById(org.mockito.ArgumentMatchers.<MarketingFlashSaleReservation>any());
    }
}
