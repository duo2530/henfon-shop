package com.henfon.shop.reporting.service;

import com.henfon.shop.reporting.dto.ReportingMemberAnalysisResponse;
import com.henfon.shop.reporting.dto.ReportingMemberLevelStatRow;
import com.henfon.shop.reporting.dto.ReportingProductRankingItem;
import com.henfon.shop.reporting.dto.ReportingProductRankingRow;
import com.henfon.shop.reporting.dto.ReportingChannelStat;
import com.henfon.shop.reporting.mapper.ReportingMetricsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * 商品排行、会员分析和渠道统计服务单元测试。
 *
 * <p>报表导出能力已迁到 shop-export 模块的异步任务，不再由本服务产出 CSV。</p>
 *
 * @author Henfon
 * @date 2026-09-01
 */
@ExtendWith(MockitoExtension.class)
class ReportingAnalysisServiceTest {

    @Mock
    private ReportingMetricsMapper reportingMetricsMapper;

    /**
     * 校验商品排行会转换数据库聚合行并补充排名。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldBuildProductRanking() {
        ReportingDashboardService service = new ReportingDashboardService(reportingMetricsMapper);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(1);
        ReportingProductRankingRow row = new ReportingProductRankingRow();
        row.setProductId(11L);
        row.setProductName("无线耳机");
        row.setCategoryName(null);
        row.setSalesVolume(8L);
        row.setSalesAmount(new BigDecimal("99.90"));
        row.setOrderCount(3L);
        when(reportingMetricsMapper.listProductRanking(start.atStartOfDay(), end.plusDays(1).atStartOfDay(), 20))
                .thenReturn(List.of(row));

        List<ReportingProductRankingItem> result = service.queryProductRanking(start, end, null);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).rank());
        assertEquals("未分类", result.get(0).categoryName());
        assertEquals(8L, result.get(0).salesVolume());
    }

    /**
     * 校验会员分析会计算复购率和平均客单价。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldCalculateMemberAnalysis() {
        ReportingDashboardService service = new ReportingDashboardService(reportingMetricsMapper);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(6);
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();
        when(reportingMetricsMapper.countMembersBefore(endTime)).thenReturn(10L);
        when(reportingMetricsMapper.countMembers(startTime, endTime)).thenReturn(2L);
        ReportingMemberLevelStatRow level = new ReportingMemberLevelStatRow();
        level.setMemberLevel("VIP");
        level.setMemberCount(5L);
        level.setActiveMemberCount(4L);
        level.setPaidOrderCount(8L);
        level.setPaidAmount(new BigDecimal("200.00"));
        when(reportingMetricsMapper.listMemberLevelStats(startTime, endTime)).thenReturn(List.of(level));
        when(reportingMetricsMapper.countRepeatMembers(startTime, endTime)).thenReturn(2L);

        ReportingMemberAnalysisResponse response = service.queryMemberAnalysis(start, end);

        assertEquals(10L, response.totalMemberCount());
        assertEquals(4L, response.activeMemberCount());
        assertEquals(new BigDecimal("50.00"), response.repurchaseRate());
        assertEquals(new BigDecimal("25.00"), response.averageOrderAmount());
    }

    /**
     * 校验支付渠道统计会规范化渠道编码并过滤空渠道。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldNormalizeChannelStats() {
        ReportingDashboardService service = new ReportingDashboardService(reportingMetricsMapper);
        LocalDate end = LocalDate.now();
        LocalDate start = end;
        ReportingChannelStat valid = new ReportingChannelStat(" wechat_native ", 3L,
                new BigDecimal("12.50"));
        ReportingChannelStat blank = new ReportingChannelStat(" ", 9L, new BigDecimal("99.00"));
        when(reportingMetricsMapper.listChannelStats(start.atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(valid, blank));

        List<ReportingChannelStat> result = service.queryChannelStats(start, end);

        assertEquals(1, result.size());
        assertEquals("WECHAT_NATIVE", result.get(0).channel());
        assertEquals(new BigDecimal("12.50"), result.get(0).paidAmount());
    }
}
