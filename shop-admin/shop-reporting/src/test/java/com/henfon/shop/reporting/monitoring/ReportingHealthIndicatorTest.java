package com.henfon.shop.reporting.monitoring;

import com.henfon.shop.reporting.mapper.ReportingEventProjectionMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 报表监控指标和健康状态测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
class ReportingHealthIndicatorTest {

    /**
     * 校验正常投影数据返回健康状态并正确计算延迟。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldReportHealthyWhenProjectionIsFresh() {
        ReportingEventProjectionMapper mapper = mock(ReportingEventProjectionMapper.class);
        LocalDateTime occurred = LocalDateTime.now().minusSeconds(20);
        when(mapper.countActiveProjections()).thenReturn(12L);
        when(mapper.countAnomalies()).thenReturn(0L);
        when(mapper.findLatestOccurredAt()).thenReturn(occurred);
        when(mapper.findLatestProjectedAt()).thenReturn(occurred.plusSeconds(2));

        ReportingHealthIndicator indicator = new ReportingHealthIndicator(mapper, new SimpleMeterRegistry(), 300);
        indicator.refresh();

        assertEquals("UP", indicator.health().getStatus().getCode());
        assertEquals(12L, indicator.health().getDetails().get("projectionCount"));
        assertEquals(2L, indicator.health().getDetails().get("lagSeconds"));
    }

    /**
     * 校验统计延迟超阈值时健康状态降级并产生异常指标。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldReportDownWhenLagOrAnomalyExists() {
        ReportingEventProjectionMapper mapper = mock(ReportingEventProjectionMapper.class);
        LocalDateTime occurred = LocalDateTime.now().minusSeconds(600);
        when(mapper.countActiveProjections()).thenReturn(3L);
        when(mapper.countAnomalies()).thenReturn(1L);
        when(mapper.findLatestOccurredAt()).thenReturn(occurred);
        when(mapper.findLatestProjectedAt()).thenReturn(occurred.plusSeconds(400));

        ReportingHealthIndicator indicator = new ReportingHealthIndicator(mapper, new SimpleMeterRegistry(), 300);
        indicator.refresh();

        assertEquals("DOWN", indicator.health().getStatus().getCode());
        assertEquals(1L, indicator.health().getDetails().get("anomalyCount"));
        assertEquals(400L, indicator.health().getDetails().get("lagSeconds"));
    }
}
