package com.henfon.shop.reporting.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.reporting.dto.ReportingDashboardMetricsResponse;
import com.henfon.shop.reporting.service.ReportingDashboardService;
import jakarta.validation.constraints.PastOrPresent;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 后台经营报表接口。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Validated
@RestController
@RequestMapping("/api/admin/reporting")
public class ReportingAdminController {

    private final ReportingDashboardService reportingDashboardService;

    /**
     * 创建后台经营报表控制器。
     *
     * @param reportingDashboardService 首页经营指标服务
     * @author Henfon
     * @date 2026-08-31
     */
    public ReportingAdminController(ReportingDashboardService reportingDashboardService) {
        this.reportingDashboardService = reportingDashboardService;
    }

    /**
     * 查询后台首页经营指标。
     *
     * @param date 指标日期，格式为yyyy-MM-dd，默认当天
     * @return 首页经营指标响应
     * @author Henfon
     * @date 2026-08-31
     */
    @GetMapping({"/overview", "/dashboard/metrics"})
    @PreAuthorize("hasAuthority('reporting:overview:query')")
    public ApiResponse<ReportingDashboardMetricsResponse> dashboardMetrics(
            @RequestParam(required = false)
            @PastOrPresent(message = "指标日期不能晚于今天")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        // 日期格式由 Spring 转换器校验，日期范围由 Bean Validation 和服务层双重兜底。
        return ApiResponse.success(reportingDashboardService.queryMetrics(date), MDC.get("requestId"));
    }
}
