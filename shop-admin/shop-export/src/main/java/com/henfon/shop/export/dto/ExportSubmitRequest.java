package com.henfon.shop.export.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * 导出任务提交请求。
 *
 * @param exportType 导出类型，取 ExportType 枚举名
 * @param keyword 关键字，含义随导出类型而定
 * @param status 数值型状态，含义随导出类型而定
 * @param statusText 文本型状态，供财务对账、优惠券有效期状态与秒杀预占状态使用
 * @param categoryId 商品类目ID
 * @param orderStatus 订单状态
 * @param memberLevel 会员等级
 * @param moduleKey 操作日志业务模块标识
 * @param financeType 财务流水类型
 * @param bizType 库存流水业务类型，取 RESERVE/RELEASE/EXPIRE_RELEASE/DEDUCT
 * @param skuId 库存流水 SKU 标识
 * @param warehouseId 库存流水仓库标识
 * @param activityId 秒杀活动ID，从活动详情页发起导出时必填
 * @param startDate 开始日期
 * @param endDate 结束日期
 * @param rankLimit 排行类导出的条数上限
 * @author Henfon
 * @date 2026-09-16
 */
public record ExportSubmitRequest(@NotBlank(message = "导出类型不能为空") String exportType,
                                  String keyword,
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
     * 转换为内部查询条件。
     *
     * @return 查询条件
     * @author Henfon
     * @date 2026-09-16
     */
    public ExportQuery toQuery() {
        return new ExportQuery(keyword, status, statusText, categoryId, orderStatus, memberLevel, moduleKey,
                financeType, bizType, skuId, warehouseId, activityId, startDate, endDate, rankLimit);
    }
}
