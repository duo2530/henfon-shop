package com.henfon.shop.inventory.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.inventory.dto.InventoryReservationItem;
import com.henfon.shop.inventory.mapper.InventoryStockLockMapper;
import com.henfon.shop.inventory.mapper.InventoryStockLogMapper;
import com.henfon.shop.inventory.mapper.InventoryStockMapper;
import com.henfon.shop.inventory.mapper.InventoryWarehouseMapper;
import com.henfon.shop.inventory.entity.InventoryStock;
import com.henfon.shop.inventory.entity.InventoryStockLog;
import com.henfon.shop.inventory.entity.InventoryWarehouse;
import com.henfon.shop.inventory.dto.InventoryStockSaveRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * 库存台账服务单元测试。
 *
 * @author Henfon
 * @date 2026-09-01
 */
class InventoryStockServiceTest {

    private final InventoryWarehouseMapper warehouseMapper = mock(InventoryWarehouseMapper.class);
    private final InventoryStockMapper stockMapper = mock(InventoryStockMapper.class);
    private final InventoryStockLockMapper lockMapper = mock(InventoryStockLockMapper.class);
    private final InventoryStockLogMapper logMapper = mock(InventoryStockLogMapper.class);
    private final InventoryStockService service = new InventoryStockService(warehouseMapper, stockMapper,
            lockMapper, logMapper);

    /**
     * 缺少 SKU 时必须拒绝预占，避免订单绕过库存锁定。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldRejectReservationWithoutSku() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reserve(1L, "AO1001", List.of(new InventoryReservationItem(10L, null, 1))));

        assertEquals("INVENTORY_SKU_REQUIRED", exception.getCode());
        verifyNoInteractions(warehouseMapper, stockMapper, lockMapper, logMapper);
    }

    /**
     * 空明细必须拒绝预占，避免创建没有库存事实的订单。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldRejectReservationWithoutItems() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reserve(1L, "AO1001", List.of()));

        assertEquals("INVENTORY_ITEMS_REQUIRED", exception.getCode());
        verifyNoInteractions(warehouseMapper, stockMapper, lockMapper, logMapper);
    }

    /**
     * 释放库存缺少订单上下文时必须拒绝，避免误释放其他订单库存。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectReleaseWithoutOrderContext() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.release(null, "ORD-1"));

        assertEquals("INVENTORY_ORDER_REQUIRED", exception.getCode());
        verifyNoInteractions(warehouseMapper, stockMapper, lockMapper, logMapper);
    }

    /**
     * 扣减库存缺少订单号时必须拒绝，确保库存流水具备可追溯业务单号。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectDeductWithoutOrderNumber() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deduct(10L, "  "));

        assertEquals("INVENTORY_ORDER_NO_REQUIRED", exception.getCode());
        verifyNoInteractions(warehouseMapper, stockMapper, lockMapper, logMapper);
    }

    /**
     * 可用库存不足时必须拒绝人工出库，避免库存被调整为负数。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectAdjustWhenAvailableStockInsufficient() {
        InventoryStock stock = new InventoryStock();
        stock.setId(7L);
        stock.setAvailableStock(2);
        when(stockMapper.selectById(7L)).thenReturn(stock);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.adjust(7L, -3, "  出库  "));

        assertEquals("INVENTORY_STOCK_NOT_ENOUGH", exception.getCode());
        verifyNoInteractions(logMapper);
    }

    /**
     * 调整结果发生整数溢出时必须拒绝写入，避免库存数值回绕。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectAdjustWhenQuantityOverflows() {
        InventoryStock stock = new InventoryStock();
        stock.setId(8L);
        stock.setAvailableStock(Integer.MAX_VALUE);
        when(stockMapper.selectById(8L)).thenReturn(stock);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.adjust(8L, 1, "补货"));

        assertEquals("INVENTORY_QUANTITY_INVALID", exception.getCode());
        verifyNoInteractions(logMapper);
    }

    /**
     * 正常调整应持久化新库存并记录去除首尾空格后的原因。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldAdjustStockAndTrimRemark() {
        InventoryStock stock = new InventoryStock();
        stock.setId(9L);
        stock.setSkuId(99L);
        stock.setAvailableStock(5);
        stock.setLockedStock(1);
        when(stockMapper.selectById(9L)).thenReturn(stock);
        when(stockMapper.updateById(stock)).thenReturn(1);

        InventoryStock result = service.adjust(9L, 2, "  到货补货  ");

        assertEquals(7, result.getAvailableStock());
        verify(stockMapper).updateById(stock);
        verify(logMapper).insert(org.mockito.ArgumentMatchers.<InventoryStockLog>argThat(log ->
                "到货补货".equals(log.getRemark()) && log.getAfterAvailable() == 7));
    }

    /**
     * 初始化库存指定停用仓库时必须拒绝，确保库存台账只落在可用仓库。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectSaveForDisabledWarehouse() {
        InventoryWarehouse warehouse = new InventoryWarehouse();
        warehouse.setId(3L);
        warehouse.setStatus(0);
        when(warehouseMapper.selectById(3L)).thenReturn(warehouse);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.save(new InventoryStockSaveRequest(3L, 1L, 2L, 10, 2, "")));

        assertEquals("INVENTORY_WAREHOUSE_INVALID", exception.getCode());
        verifyNoInteractions(stockMapper, logMapper);
    }
}
