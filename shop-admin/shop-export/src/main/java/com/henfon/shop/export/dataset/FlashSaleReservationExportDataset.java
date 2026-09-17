package com.henfon.shop.export.dataset;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.export.dto.ExportQuery;
import com.henfon.shop.export.entity.ExportType;
import com.henfon.shop.export.excel.ExcelColumn;
import com.henfon.shop.marketing.dto.MarketingFlashSaleDetailResponse;
import com.henfon.shop.marketing.dto.MarketingFlashSaleReservationRow;
import com.henfon.shop.marketing.service.MarketingFlashSaleService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * 秒杀活动预占记录导出数据集。
 *
 * <p>与管理端详情页共用同一个分页查询，会员名称、订单编号与商品名称的补齐逻辑只此一处。
 * 活动ID是必需条件；预占状态可选，取 reserved/released，取值非法时由服务层直接报错，
 * 不会静默退化成导出全部记录。</p>
 *
 * @author Henfon
 * @date 2026-09-17
 */
@Component
public class FlashSaleReservationExportDataset extends AbstractExportDataset<FlashSaleReservationExportDataset.Row> {

    private final MarketingFlashSaleService flashSaleService;

    /**
     * 创建秒杀活动预占记录导出数据集。
     *
     * @param flashSaleService 秒杀活动服务
     * @author Henfon
     * @date 2026-09-17
     */
    public FlashSaleReservationExportDataset(MarketingFlashSaleService flashSaleService) {
        this.flashSaleService = flashSaleService;
    }

    /**
     * 秒杀预占记录导出行。
     *
     * @param activityCode 活动编码
     * @param activityName 活动名称
     * @param createdAt 预占时间
     * @param memberNo 会员编号
     * @param memberName 会员名称
     * @param memberPhone 会员手机号
     * @param orderNo 订单编号
     * @param productName 商品名称
     * @param skuName SKU名称
     * @param quantity 预占数量
     * @param statusText 预占状态
     * @param releasedAt 释放时间
     * @author Henfon
     * @date 2026-09-17
     */
    public record Row(String activityCode, String activityName, LocalDateTime createdAt, String memberNo,
                      String memberName, String memberPhone, String orderNo, String productName, String skuName,
                      Integer quantity, String statusText, LocalDateTime releasedAt) {
    }

    @Override
    public ExportType type() {
        return ExportType.FLASH_SALE_RESERVATION;
    }

    @Override
    public List<ExcelColumn<Row>> columns() {
        return List.of(
                ExcelColumn.text("活动编码", Row::activityCode, 20),
                ExcelColumn.text("活动名称", Row::activityName, 26),
                ExcelColumn.dateTime("预占时间", Row::createdAt, 21),
                ExcelColumn.text("会员编号", Row::memberNo, 20),
                ExcelColumn.text("会员名称", Row::memberName, 18),
                ExcelColumn.text("会员手机号", Row::memberPhone, 15),
                ExcelColumn.text("订单编号", Row::orderNo, 24),
                ExcelColumn.text("商品名称", Row::productName, 30),
                ExcelColumn.text("规格名称", Row::skuName, 22),
                ExcelColumn.integer("预占数量", Row::quantity, 11),
                ExcelColumn.text("预占状态", Row::statusText, 10),
                ExcelColumn.dateTime("释放时间", Row::releasedAt, 21));
    }

    @Override
    public void stream(Consumer<Row> consumer, ExportQuery query) {
        if (query.activityId() == null) {
            throw new BusinessException("EXPORT_FLASH_SALE_ACTIVITY_REQUIRED",
                    "秒杀活动导出必须指定活动，请从活动详情页发起导出");
        }
        Long activityId = query.activityId();
        String statusText = query.statusText();
        MarketingFlashSaleDetailResponse activity = flashSaleService.detail(activityId);
        streamPages(consumer, (pageNo, pageSize) ->
                flashSaleService.pageReservations(activityId, statusText, pageNo, pageSize)
                        .getRecords().stream().map(row -> toRow(activity, row)).toList());
    }

    /**
     * 预占记录行转导出行。
     *
     * @param activity 活动详情，用于取活动编码与名称
     * @param row 预占记录行
     * @return 导出行
     * @author Henfon
     * @date 2026-09-17
     */
    private static Row toRow(MarketingFlashSaleDetailResponse activity, MarketingFlashSaleReservationRow row) {
        return new Row(activity.activityCode(), activity.activityName(), row.createdAt(), row.memberNo(),
                row.memberName(), row.memberPhone(), row.orderNo(), row.productName(), row.skuName(),
                row.quantity(), row.statusText(), row.releasedAt());
    }
}
