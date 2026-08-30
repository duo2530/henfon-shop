package com.henfon.shop.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 库存台账初始化请求。
 *
 * @param warehouseId 仓库ID，可为空，默认使用默认仓库
 * @param productId 商品ID
 * @param skuId SKU ID
 * @param availableStock 可用库存
 * @param safetyStock 安全库存
 * @param remark 备注
 * @author Henfon
 * @date 2026-08-30
 */
public record InventoryStockSaveRequest(Long warehouseId, Long productId, @NotNull Long skuId,
                                        @Min(0) int availableStock, @Min(0) int safetyStock, String remark) {
}
