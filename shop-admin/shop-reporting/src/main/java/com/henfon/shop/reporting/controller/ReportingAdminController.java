package com.henfon.shop.reporting.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.reporting.dto.ReportingDashboardMetricsResponse;
import com.henfon.shop.reporting.dto.ReportingSalesTrendPoint;
import com.henfon.shop.reporting.dto.ReportingProductRankingItem;
import com.henfon.shop.reporting.dto.ReportingMemberAnalysisResponse;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

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

    /**
     * 查询后台销售趋势。
     *
     * @param startDate 开始日期，格式为yyyy-MM-dd，默认结束日期前六天
     * @param endDate 结束日期，格式为yyyy-MM-dd，默认当天
     * @return 每日销售趋势列表
     * @author Henfon
     * @date 2026-08-31
     */
    @GetMapping({"/sales-trend", "/dashboard/sales-trend"})
    @PreAuthorize("hasAuthority('reporting:overview:query')")
    public ApiResponse<List<ReportingSalesTrendPoint>> salesTrend(
            @RequestParam(required = false)
            @PastOrPresent(message = "销售趋势开始日期不能晚于今天")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @PastOrPresent(message = "销售趋势结束日期不能晚于今天")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        // 服务层负责补齐空日期并执行最大30天范围校验，控制器只负责参数绑定和权限控制。
        return ApiResponse.success(reportingDashboardService.querySalesTrend(startDate, endDate), MDC.get("requestId"));
    }

    /**
     * 查询商品销售排行。
     *
     * @param startDate 开始日期，默认结束日期前29天
     * @param endDate 结束日期，默认当天
     * @param limit 返回条数，默认20，最大100
     * @return 商品排行响应
     * @author Henfon
     * @date 2026-09-01
     */
    @GetMapping({"/product-ranking", "/products/ranking"})
    @PreAuthorize("hasAuthority('reporting:product:query')")
    public ApiResponse<List<ReportingProductRankingItem>> productRanking(
            @RequestParam(required = false)
            @PastOrPresent(message = "商品排行开始日期不能晚于今天")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @PastOrPresent(message = "商品排行结束日期不能晚于今天")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        // 服务层统一处理日期边界、最大范围和条数限制，避免不同入口规则不一致。
        return ApiResponse.success(reportingDashboardService.queryProductRanking(startDate, endDate, limit),
                MDC.get("requestId"));
    }

    /**
     * 查询会员分析报表。
     *
     * @param startDate 开始日期，默认结束日期前29天
     * @param endDate 结束日期，默认当天
     * @return 会员分析响应
     * @author Henfon
     * @date 2026-09-01
     */
    @GetMapping({"/member-analysis", "/members/analysis"})
    @PreAuthorize("hasAuthority('reporting:overview:query')")
    public ApiResponse<ReportingMemberAnalysisResponse> memberAnalysis(
            @RequestParam(required = false)
            @PastOrPresent(message = "会员分析开始日期不能晚于今天")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @PastOrPresent(message = "会员分析结束日期不能晚于今天")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(reportingDashboardService.queryMemberAnalysis(startDate, endDate),
                MDC.get("requestId"));
    }

    /**
     * 导出报表 CSV 文件。
     *
     * @param reportType 报表类型：PRODUCT_RANKING、MEMBER_ANALYSIS 或 SALES_TREND
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param limit 商品排行条数
     * @return CSV 文件响应
     * @author Henfon
     * @date 2026-09-01
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyAuthority('reporting:product:query','reporting:overview:query')")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "PRODUCT_RANKING") String reportType,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                         @RequestParam(required = false, defaultValue = "20") Integer limit) {
        byte[] content = reportingDashboardService.export(reportType, startDate, endDate, limit);
        // 文件名只保留字母、数字和下划线，避免请求参数进入响应头造成注入。
        String safeType = reportType == null ? "report" : reportType.trim()
                .replaceAll("[^A-Za-z0-9_-]", "_").toLowerCase(java.util.Locale.ROOT);
        String fileName = "report-" + safeType + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(content);
    }
}
