package com.henfon.shop.inventory.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.inventory.dto.InventoryReservationItem;
import com.henfon.shop.inventory.mapper.InventoryStockLockMapper;
import com.henfon.shop.inventory.mapper.InventoryStockLogMapper;
import com.henfon.shop.inventory.mapper.InventoryStockMapper;
import com.henfon.shop.inventory.mapper.InventoryWarehouseMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
}
