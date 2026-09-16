package com.henfon.shop.export.dataset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.export.dto.ExportQuery;
import com.henfon.shop.export.entity.ExportType;
import com.henfon.shop.export.excel.ExcelColumn;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Consumer;

/**
 * 商品库导出数据集。
 *
 * @author Henfon
 * @date 2026-09-16
 */
@Component
public class ProductExportDataset extends AbstractExportDataset<ProductExportDataset.Row> {

    private final CatalogProductMapper catalogProductMapper;

    /**
     * 创建商品库导出数据集。
     *
     * @param catalogProductMapper 商品数据访问对象
     * @author Henfon
     * @date 2026-09-16
     */
    public ProductExportDataset(CatalogProductMapper catalogProductMapper) {
        this.catalogProductMapper = catalogProductMapper;
    }

    /**
     * 商品库导出行。
     *
     * @param productCode 商品编号
     * @param productName 商品名称
     * @param categoryName 分类
     * @param price 售价
     * @param costPrice 成本价
     * @param marginPercent 毛利率，成本价缺失时为空
     * @param currentStock 库存
     * @param safetyStock 安全库存
     * @param salesCount 销量
     * @param tags 标签
     * @param statusLabel 状态文案
     * @author Henfon
     * @date 2026-09-16
     */
    public record Row(String productCode, String productName, String categoryName, BigDecimal price,
                      BigDecimal costPrice, BigDecimal marginPercent, Integer currentStock, Integer safetyStock,
                      Long salesCount, String tags, String statusLabel) {
    }

    @Override
    public ExportType type() {
        return ExportType.PRODUCT;
    }

    @Override
    public List<ExcelColumn<Row>> columns() {
        return List.of(
                ExcelColumn.text("商品编号", Row::productCode, 20),
                ExcelColumn.text("商品名称", Row::productName, 32),
                ExcelColumn.text("分类", Row::categoryName, 16),
                ExcelColumn.money("售价(¥)", Row::price, 12),
                ExcelColumn.money("成本价(¥)", Row::costPrice, 12),
                ExcelColumn.decimal("毛利率", Row::marginPercent, 10),
                ExcelColumn.integer("库存", Row::currentStock, 10),
                ExcelColumn.integer("安全库存", Row::safetyStock, 10),
                ExcelColumn.integer("销量", Row::salesCount, 10),
                ExcelColumn.text("标签", Row::tags, 24),
                ExcelColumn.text("状态", Row::statusLabel, 10));
    }

    @Override
    public void stream(Consumer<Row> consumer, ExportQuery query) {
        LambdaQueryWrapper<CatalogProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.status() != null, CatalogProduct::getStatus, query.status());
        wrapper.eq(query.categoryId() != null, CatalogProduct::getCategoryId, query.categoryId());
        if (StringUtils.hasText(query.keyword())) {
            String keyword = query.keyword().trim();
            wrapper.and(condition -> condition.like(CatalogProduct::getProductName, keyword)
                    .or().like(CatalogProduct::getProductCode, keyword)
                    .or().like(CatalogProduct::getDefaultSkuCode, keyword));
        }
        wrapper.orderByDesc(CatalogProduct::getId);

        streamPages(consumer, (pageNo, pageSize) -> {
            Page<CatalogProduct> page = catalogProductMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
            return page.getRecords().stream().map(ProductExportDataset::toRow).toList();
        });
    }

    /**
     * 实体转导出行为。
     *
     * @param product 商品实体
     * @return 导出行
     * @author Henfon
     * @date 2026-09-16
     */
    private static Row toRow(CatalogProduct product) {
        return new Row(product.getProductCode(), product.getProductName(), product.getCategoryName(),
                product.getPrice(), product.getCostPrice(), resolveMargin(product.getPrice(), product.getCostPrice()),
                product.getCurrentStock(), product.getSafetyStock(), product.getSalesCount(),
                product.getTagsCsv(), product.getStatus() != null && product.getStatus() == 1 ? "上架中" : "已下架");
    }

    /**
     * 计算毛利率。
     *
     * <p>成本价缺失时返回空值而不是按售价推算，避免把臆造的成本写进对外报表。</p>
     *
     * @param price 售价
     * @param costPrice 成本价
     * @return 毛利率百分比
     * @author Henfon
     * @date 2026-09-16
     */
    private static BigDecimal resolveMargin(BigDecimal price, BigDecimal costPrice) {
        if (price == null || costPrice == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return price.subtract(costPrice)
                .divide(price, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
