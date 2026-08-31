package com.henfon.shop.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 完成库存盘点请求。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public record InventoryStocktakeCompleteRequest(
        @NotEmpty List<@Valid InventoryStocktakeCountItem> items,
        @Size(max = 500) String remark
) {

    /**
     * 盘点明细实盘数量。
     *
     * @param itemId 盘点明细ID
     * @param actualQuantity 实盘数量
     * @param remark 明细备注
     * @author Henfon
     * @date 2026-08-31
     */
    public record InventoryStocktakeCountItem(
            @NotNull @PositiveOrZero Long itemId,
            @NotNull @PositiveOrZero Integer actualQuantity,
            @Size(max = 500) String remark
    ) {
    }
}
