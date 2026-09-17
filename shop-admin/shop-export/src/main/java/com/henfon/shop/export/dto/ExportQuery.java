package com.henfon.shop.export.dto;

import java.time.LocalDate;

/**
 * 导出查询条件。
 *
 * <p>各导出类型按需取用其中字段，未使用的字段保持为空。提交任务时整体序列化进
 * {@code export_task.query_params}，便于追溯当时的筛选口径。</p>
 *
 * @param keyword 关键字，含义随导出类型而定
 * @param status 数值型状态，含义随导出类型而定
 * @param statusText 文本型状态，含义随导出类型而定：财务对账为 reconciled/pending_settle/discrepancy，
 *                   优惠券为页面按有效期推导的 active/scheduled/expired，
 *                   秒杀预占记录为 reserved/released
 * @param categoryId 商品类目ID
 * @param orderStatus 订单状态
 * @param memberLevel 会员等级
 * @param moduleKey 操作日志业务模块标识
 * @param financeType 财务流水类型
 * @param bizType 库存流水业务类型，取 RESERVE/RELEASE/EXPIRE_RELEASE/DEDUCT
 * @param skuId 库存流水 SKU 标识
 * @param warehouseId 库存流水仓库标识
 * @param activityId 秒杀活动ID，秒杀类的两个导出均以此为必需条件
 * @param startDate 开始日期
 * @param endDate 结束日期
 * @param rankLimit 排行类导出的条数上限
 * @author Henfon
 * @date 2026-09-16
 */
public record ExportQuery(String keyword,
                          Integer status,
                          String statusText,
                          Long categoryId,
                          Integer orderStatus,
                          String memberLevel,
                          String moduleKey,
                          String financeType,
                          String bizType,
                          Long skuId,
                          Long warehouseId,
                          Long activityId,
                          LocalDate startDate,
                          LocalDate endDate,
                          Integer rankLimit) {

    /**
     * 空条件，用于「导出全部」场景。
     *
     * @return 空条件
     * @author Henfon
     * @date 2026-09-16
     */
    public static ExportQuery empty() {
        return new ExportQuery(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null);
    }
}
