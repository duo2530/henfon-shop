package com.henfon.shop.export.dataset;

import com.henfon.shop.export.dto.ExportQuery;
import com.henfon.shop.export.entity.ExportType;
import com.henfon.shop.export.excel.ExcelColumn;
import com.henfon.shop.reporting.dto.ReportingProductRankingItem;
import com.henfon.shop.reporting.service.ReportingDashboardService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

/**
 * 商品动销排行导出数据集。
 *
 * @author Henfon
 * @date 2026-09-16
 */
@Component
public class ProductRankingExportDataset extends AbstractExportDataset<ProductRankingExportDataset.Row> {

    /** 未指定条数时的默认排行上限，覆盖常见运营分析需求。 */
    private static final int DEFAULT_RANK_LIMIT = 1000;

    private final ReportingDashboardService reportingDashboardService;

    /**
     * 创建动销排行导出数据集。
     *
     * @param reportingDashboardService 经营分析服务
     * @author Henfon
     * @date 2026-09-16
     */
    public ProductRankingExportDataset(ReportingDashboardService reportingDashboardService) {
        this.reportingDashboardService = reportingDashboardService;
    }

    /**
     * 动销排行导出行。
     *
     * @param rank 排名
     * @param productId 商品ID
     * @param productName 商品名称
     * @param categoryName 类目
     * @param salesVolume 销量
     * @param salesAmount 销售额
     * @param orderCount 订单数
     * @author Henfon
     * @date 2026-09-16
     */
    public record Row(Integer rank, Long productId, String productName, String categoryName,
                      Long salesVolume, BigDecimal salesAmount, Long orderCount) {
    }

    @Override
    public ExportType type() {
        return ExportType.PRODUCT_RANKING;
    }

    @Override
    public List<ExcelColumn<Row>> columns() {
        return List.of(
                ExcelColumn.integer("排名", Row::rank, 8),
                ExcelColumn.integer("商品ID", Row::productId, 12),
                ExcelColumn.text("商品名称", Row::productName, 34),
                ExcelColumn.text("类目", Row::categoryName, 16),
                ExcelColumn.integer("销量", Row::salesVolume, 10),
                ExcelColumn.money("销售额(¥)", Row::salesAmount, 16),
                ExcelColumn.integer("订单数", Row::orderCount, 10));
    }

    @Override
    public void stream(Consumer<Row> consumer, ExportQuery query) {
        int limit = query.rankLimit() != null && query.rankLimit() > 0 ? query.rankLimit() : DEFAULT_RANK_LIMIT;
        List<ReportingProductRankingItem> items =
                reportingDashboardService.queryProductRanking(query.startDate(), query.endDate(), limit);
        for (ReportingProductRankingItem item : items) {
            consumer.accept(new Row(item.rank(), item.productId(), item.productName(), item.categoryName(),
                    item.salesVolume(), item.salesAmount(), item.orderCount()));
        }
    }
}
