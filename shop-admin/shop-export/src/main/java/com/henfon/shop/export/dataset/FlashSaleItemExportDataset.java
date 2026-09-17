package com.henfon.shop.export.dataset;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.export.dto.ExportQuery;
import com.henfon.shop.export.entity.ExportType;
import com.henfon.shop.export.excel.ExcelColumn;
import com.henfon.shop.marketing.dto.MarketingFlashSaleDetailResponse;
import com.henfon.shop.marketing.dto.MarketingFlashSaleItemRow;
import com.henfon.shop.marketing.service.MarketingFlashSaleService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * 秒杀活动商品明细导出数据集。
 *
 * <p>复用管理端详情页的商品明细分页查询，商品名称、原价与折扣口径与页面完全一致，
 * 不在导出侧另写一套名称解析。活动ID是必需条件：明细必须落在某个活动下才有意义，
 * 缺省时直接失败，而不是导出一份混着所有活动的表格。</p>
 *
 * @author Henfon
 * @date 2026-09-17
 */
@Component
public class FlashSaleItemExportDataset extends AbstractExportDataset<FlashSaleItemExportDataset.Row> {

    private final MarketingFlashSaleService flashSaleService;

    /**
     * 创建秒杀活动商品明细导出数据集。
     *
     * @param flashSaleService 秒杀活动服务
     * @author Henfon
     * @date 2026-09-17
     */
    public FlashSaleItemExportDataset(MarketingFlashSaleService flashSaleService) {
        this.flashSaleService = flashSaleService;
    }

    /**
     * 秒杀活动商品明细导出行。
     *
     * @param activityCode 活动编码
     * @param activityName 活动名称
     * @param productCode 商品编码
     * @param productName 商品名称
     * @param skuCode SKU编码
     * @param skuName SKU名称
     * @param activityPrice 秒杀活动价
     * @param originalPrice 活动价对应的原价
     * @param discountRate 折扣率，单位为百分数
     * @param totalStock 活动库存
     * @param soldStock 已售活动库存
     * @param remainingStock 剩余活动库存
     * @param limitPerMember 单会员限购
     * @param statusText 启用状态
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     * @author Henfon
     * @date 2026-09-17
     */
    public record Row(String activityCode, String activityName, String productCode, String productName,
                      String skuCode, String skuName, BigDecimal activityPrice, BigDecimal originalPrice,
                      BigDecimal discountRate, Integer totalStock, Integer soldStock, Integer remainingStock,
                      Integer limitPerMember, String statusText, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    @Override
    public ExportType type() {
        return ExportType.FLASH_SALE_ITEM;
    }

    @Override
    public List<ExcelColumn<Row>> columns() {
        return List.of(
                ExcelColumn.text("活动编码", Row::activityCode, 20),
                ExcelColumn.text("活动名称", Row::activityName, 26),
                ExcelColumn.text("商品编码", Row::productCode, 20),
                ExcelColumn.text("商品名称", Row::productName, 30),
                ExcelColumn.text("SKU编码", Row::skuCode, 20),
                ExcelColumn.text("规格名称", Row::skuName, 22),
                ExcelColumn.money("活动价(¥)", Row::activityPrice, 13),
                ExcelColumn.money("原价(¥)", Row::originalPrice, 13),
                ExcelColumn.decimal("折扣率(%)", Row::discountRate, 11),
                ExcelColumn.integer("活动库存", Row::totalStock, 11),
                ExcelColumn.integer("已售", Row::soldStock, 10),
                ExcelColumn.integer("剩余", Row::remainingStock, 10),
                ExcelColumn.integer("单会员限购", Row::limitPerMember, 12),
                ExcelColumn.text("状态", Row::statusText, 10),
                ExcelColumn.dateTime("创建时间", Row::createdAt, 21),
                ExcelColumn.dateTime("更新时间", Row::updatedAt, 21));
    }

    @Override
    public void stream(Consumer<Row> consumer, ExportQuery query) {
        if (query.activityId() == null) {
            throw new BusinessException("EXPORT_FLASH_SALE_ACTIVITY_REQUIRED",
                    "秒杀活动导出必须指定活动，请从活动详情页发起导出");
        }
        Long activityId = query.activityId();
        // 活动编码与名称随每行输出，循环前取一次即可，同时顺带校验活动仍然存在。
        MarketingFlashSaleDetailResponse activity = flashSaleService.detail(activityId);
        streamPages(consumer, (pageNo, pageSize) -> flashSaleService.pageItems(activityId, pageNo, pageSize)
                .getRecords().stream().map(item -> toRow(activity, item)).toList());
    }

    /**
     * 明细行转导出行。
     *
     * @param activity 活动详情，用于取活动编码与名称
     * @param item 活动商品明细行
     * @return 导出行
     * @author Henfon
     * @date 2026-09-17
     */
    private static Row toRow(MarketingFlashSaleDetailResponse activity, MarketingFlashSaleItemRow item) {
        return new Row(activity.activityCode(), activity.activityName(), item.productCode(), item.productName(),
                item.skuCode(), item.skuName(), item.activityPrice(), item.originalPrice(), item.discountRate(),
                item.totalStock(), item.soldStock(), item.remainingStock(), item.limitPerMember(), item.statusText(),
                item.createdAt(), item.updatedAt());
    }
}
