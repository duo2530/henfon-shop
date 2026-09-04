package com.henfon.shop.reporting.service;

import com.henfon.shop.common.exception.BusinessException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 商品排行、会员分析和报表导出服务单元测试。
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
     * 校验商品排行导出包含 UTF-8 BOM 和列标题。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldExportProductRankingCsv() {
        ReportingDashboardService service = new ReportingDashboardService(reportingMetricsMapper);
        LocalDate end = LocalDate.now();
        LocalDate start = end;
        when(reportingMetricsMapper.listProductRanking(start.atStartOfDay(), end.plusDays(1).atStartOfDay(), 20))
                .thenReturn(List.of());

        String csv = new String(service.export("PRODUCT_RANKING", start, end, null), java.nio.charset.StandardCharsets.UTF_8);

        assertEquals('\uFEFF', csv.charAt(0));
        assertEquals(true, csv.contains("商品ID"));
    }

    /**
     * 校验不支持的导出类型会返回明确业务错误。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldRejectUnknownExportType() {
        ReportingDashboardService service = new ReportingDashboardService(reportingMetricsMapper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.export("UNKNOWN", LocalDate.now(), LocalDate.now(), 20));

        assertEquals("REPORTING_TYPE_INVALID", exception.getCode());
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
