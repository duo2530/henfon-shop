package com.henfon.shop.inventory.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.inventory.dto.InventoryStocktakeCancelRequest;
import com.henfon.shop.inventory.entity.InventoryStocktake;
import com.henfon.shop.inventory.mapper.InventoryStockLogMapper;
import com.henfon.shop.inventory.mapper.InventoryStockMapper;
import com.henfon.shop.inventory.mapper.InventoryStocktakeItemMapper;
import com.henfon.shop.inventory.mapper.InventoryStocktakeMapper;
import com.henfon.shop.inventory.mapper.InventoryWarehouseMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 库存盘点服务单元测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
class InventoryStocktakeServiceTest {

    private final InventoryStocktakeMapper stocktakeMapper = mock(InventoryStocktakeMapper.class);
    private final InventoryStocktakeItemMapper itemMapper = mock(InventoryStocktakeItemMapper.class);
    private final InventoryWarehouseMapper warehouseMapper = mock(InventoryWarehouseMapper.class);
    private final InventoryStockMapper stockMapper = mock(InventoryStockMapper.class);
    private final InventoryStockLogMapper logMapper = mock(InventoryStockLogMapper.class);
    private final InventoryStocktakeService service = new InventoryStocktakeService(stocktakeMapper, itemMapper,
            warehouseMapper, stockMapper, logMapper);

    /**
     * 进行中的盘点单取消后应记录取消状态和清理后的备注。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldCancelOpenStocktake() {
        InventoryStocktake stocktake = new InventoryStocktake();
        stocktake.setId(11L);
        stocktake.setStatus(0);
        stocktake.setRemark("原备注");
        when(stocktakeMapper.selectById(11L)).thenReturn(stocktake);
        when(stocktakeMapper.updateById(stocktake)).thenReturn(1);

        InventoryStocktake result = service.cancel(11L, new InventoryStocktakeCancelRequest("  作废盘点  "));

        assertEquals(2, result.getStatus());
        assertEquals("作废盘点", result.getRemark());
        verify(stocktakeMapper).updateById(stocktake);
        verifyNoInteractions(itemMapper, warehouseMapper, stockMapper, logMapper);
    }

    /**
     * 已完成盘点单不可再次取消，避免状态回退造成库存审计断链。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectCancelForCompletedStocktake() {
        InventoryStocktake stocktake = new InventoryStocktake();
        stocktake.setId(12L);
        stocktake.setStatus(1);
        when(stocktakeMapper.selectById(12L)).thenReturn(stocktake);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.cancel(12L, new InventoryStocktakeCancelRequest("无需修改")));

        assertEquals("INVENTORY_STOCKTAKE_STATUS_INVALID", exception.getCode());
        verifyNoInteractions(itemMapper, warehouseMapper, stockMapper, logMapper);
    }
}
