package com.henfon.shop.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 库存盘点实盘数量批量导入请求。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public record InventoryStocktakeImportRequest(
        @NotEmpty @Size(max = 5000) List<@Valid InventoryStocktakeImportItem> items
) {

    /**
     * 盘点明细导入项。
     *
     * @param itemId 盘点明细ID
     * @param skuId SKU ID，用于校验导入文件归属
     * @param actualQuantity 实盘数量
     * @param version 明细乐观锁版本
     * @param remark 明细备注
     * @author Henfon
     * @date 2026-08-31
     */
    public record InventoryStocktakeImportItem(
            @NotNull @PositiveOrZero Long itemId,
            @NotNull @PositiveOrZero Long skuId,
            @NotNull @PositiveOrZero Integer actualQuantity,
            @NotNull @PositiveOrZero Integer version,
            @Size(max = 500) String remark
    ) {
    }
}
