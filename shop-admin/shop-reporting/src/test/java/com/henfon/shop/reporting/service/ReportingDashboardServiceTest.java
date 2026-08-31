package com.henfon.shop.reporting.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.reporting.dto.ReportingDashboardMetricsResponse;
import com.henfon.shop.reporting.mapper.ReportingMetricsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 首页经营指标服务单元测试。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class ReportingDashboardServiceTest {

    @Mock
    private ReportingMetricsMapper reportingMetricsMapper;

    /**
     * 校验指标服务按自然日组装今日与累计数据。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldAssembleDailyAndCumulativeMetrics() {
        ReportingDashboardService service = new ReportingDashboardService(reportingMetricsMapper);
        LocalDate date = LocalDate.of(2026, 8, 30);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        when(reportingMetricsMapper.countOrders(start, end)).thenReturn(3L);
        when(reportingMetricsMapper.sumSales(start, end)).thenReturn(new BigDecimal("128.50"));
        when(reportingMetricsMapper.countProducts(start, end)).thenReturn(2L);
        when(reportingMetricsMapper.countMembers(start, end)).thenReturn(1L);
        when(reportingMetricsMapper.countOrdersBefore(end)).thenReturn(10L);
        when(reportingMetricsMapper.sumSalesBefore(end)).thenReturn(new BigDecimal("999.90"));
        when(reportingMetricsMapper.countProductsBefore(end)).thenReturn(8L);
        when(reportingMetricsMapper.countMembersBefore(end)).thenReturn(6L);

        ReportingDashboardMetricsResponse response = service.queryMetrics(date);

        assertEquals(date, response.date());
        assertEquals(3L, response.todayOrderCount());
        assertEquals(new BigDecimal("128.50"), response.todaySalesAmount());
        assertEquals(10L, response.totalOrderCount());
        assertEquals(new BigDecimal("999.90"), response.totalSalesAmount());
    }

    /**
     * 校验数据库聚合返回空值时服务统一降级为零。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldNormalizeNullAggregatesToZero() {
        ReportingDashboardService service = new ReportingDashboardService(reportingMetricsMapper);
        LocalDate date = LocalDate.of(2026, 8, 30);
        when(reportingMetricsMapper.countOrders(date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenReturn(null);
        when(reportingMetricsMapper.sumSales(date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenReturn(null);
        when(reportingMetricsMapper.countProducts(date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenReturn(null);
        when(reportingMetricsMapper.countMembers(date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenReturn(null);
        when(reportingMetricsMapper.countOrdersBefore(date.plusDays(1).atStartOfDay())).thenReturn(null);
        when(reportingMetricsMapper.sumSalesBefore(date.plusDays(1).atStartOfDay())).thenReturn(null);
        when(reportingMetricsMapper.countProductsBefore(date.plusDays(1).atStartOfDay())).thenReturn(null);
        when(reportingMetricsMapper.countMembersBefore(date.plusDays(1).atStartOfDay())).thenReturn(null);

        ReportingDashboardMetricsResponse response = service.queryMetrics(date);

        assertEquals(0L, response.todayOrderCount());
        assertEquals(BigDecimal.ZERO, response.todaySalesAmount());
        assertEquals(0L, response.totalMemberCount());
    }

    /**
     * 校验查询未来日期时拒绝返回虚假指标。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectFutureDate() {
        ReportingDashboardService service = new ReportingDashboardService(reportingMetricsMapper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.queryMetrics(LocalDate.now().plusDays(1)));

        assertEquals("REPORTING_DATE_INVALID", exception.getCode());
    }
}
