package com.henfon.shop.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.inventory.dto.InventoryPurchaseCreateRequest;
import com.henfon.shop.inventory.entity.InventoryPurchaseItem;
import com.henfon.shop.inventory.entity.InventoryPurchaseOrder;
import com.henfon.shop.inventory.entity.InventoryStock;
import com.henfon.shop.inventory.entity.InventorySupplier;
import com.henfon.shop.inventory.entity.InventorySupplierStock;
import com.henfon.shop.inventory.entity.InventoryWarehouse;
import com.henfon.shop.inventory.mapper.InventoryPurchaseItemMapper;
import com.henfon.shop.inventory.mapper.InventoryPurchaseOrderMapper;
import com.henfon.shop.inventory.mapper.InventoryStockMapper;
import com.henfon.shop.inventory.mapper.InventorySupplierMapper;
import com.henfon.shop.inventory.mapper.InventorySupplierStockMapper;
import com.henfon.shop.inventory.mapper.InventoryWarehouseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 采购入库应用服务。 @author Henfon @date 2026-09-04 */
@Service
public class InventoryPurchaseService {
    private static final int STATUS_CREATED = 0;
    private static final int STATUS_RECEIVED = 1;
    private final InventoryPurchaseOrderMapper orderMapper;
    private final InventoryPurchaseItemMapper itemMapper;
    private final InventorySupplierMapper supplierMapper;
    private final InventoryWarehouseMapper warehouseMapper;
    private final InventoryStockMapper stockMapper;
    private final InventorySupplierStockMapper supplierStockMapper;

    /** 创建采购服务。 @author Henfon @date 2026-09-04 */
    public InventoryPurchaseService(InventoryPurchaseOrderMapper orderMapper, InventoryPurchaseItemMapper itemMapper,
                                    InventorySupplierMapper supplierMapper, InventoryWarehouseMapper warehouseMapper,
                                    InventoryStockMapper stockMapper, InventorySupplierStockMapper supplierStockMapper) {
        this.orderMapper = orderMapper; this.itemMapper = itemMapper; this.supplierMapper = supplierMapper;
        this.warehouseMapper = warehouseMapper; this.stockMapper = stockMapper;
        this.supplierStockMapper = supplierStockMapper;
    }

    /** 分页查询采购单。 @author Henfon @date 2026-09-04 */
    public IPage<InventoryPurchaseOrder> page(Integer status, long current, long size) {
        // 统一限制分页大小，避免采购历史一次性加载过多。
        return orderMapper.selectPage(new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 200)),
                new LambdaQueryWrapper<InventoryPurchaseOrder>().eq(status != null, InventoryPurchaseOrder::getStatus, status)
                        .orderByDesc(InventoryPurchaseOrder::getCreatedAt));
    }

    /** 创建采购单。 @author Henfon @date 2026-09-04 */
    @Transactional
    public InventoryPurchaseOrder create(InventoryPurchaseCreateRequest request) {
        InventorySupplier supplier = supplierMapper.selectById(request.supplierId());
        InventoryWarehouse warehouse = warehouseMapper.selectById(request.warehouseId());
        if (supplier == null || !Integer.valueOf(1).equals(supplier.getStatus())) throw new BusinessException("INVENTORY_PURCHASE_SUPPLIER_INVALID", "供应商不存在或已停用");
        if (warehouse == null || !Integer.valueOf(1).equals(warehouse.getStatus())) throw new BusinessException("INVENTORY_PURCHASE_WAREHOUSE_INVALID", "仓库不存在或已停用");
        InventoryPurchaseOrder order = new InventoryPurchaseOrder(); order.setPurchaseNo("PO" + IdWorker.getIdStr()); order.setSupplierId(request.supplierId()); order.setWarehouseId(request.warehouseId()); order.setStatus(STATUS_CREATED); order.setRemark(trim(request.remark()));
        BigDecimal total = BigDecimal.ZERO;
        orderMapper.insert(order);
        java.util.Set<Long> skuSet = new java.util.HashSet<>();
        for (InventoryPurchaseCreateRequest.Item input : request.items()) {
            if (!skuSet.add(input.skuId())) {
                throw new BusinessException("INVENTORY_PURCHASE_SKU_DUPLICATE", "采购单不能重复添加同一SKU");
            }
            InventorySupplierStock binding = supplierStockMapper.selectOne(new LambdaQueryWrapper<InventorySupplierStock>()
                    .eq(InventorySupplierStock::getSupplierId, request.supplierId())
                    .eq(InventorySupplierStock::getSkuId, input.skuId())
                    .eq(InventorySupplierStock::getStatus, 1));
            if (binding == null) throw new BusinessException("INVENTORY_PURCHASE_SUPPLIER_SKU_INVALID", "采购SKU未绑定该供应商或供货关系已停用");
            if (input.unitPrice().signum() < 0) throw new BusinessException("INVENTORY_PURCHASE_PRICE_INVALID", "采购单价不能为负数");
            InventoryPurchaseItem item = new InventoryPurchaseItem(); item.setPurchaseOrderId(order.getId()); item.setProductId(input.productId()); item.setSkuId(input.skuId()); item.setQuantity(input.quantity()); item.setReceivedQuantity(0); item.setUnitPrice(input.unitPrice()); item.setRemark(trim(input.remark())); itemMapper.insert(item);
            total = total.add(input.unitPrice().multiply(BigDecimal.valueOf(input.quantity())));
        }
        order.setTotalAmount(total); orderMapper.updateById(order); return order;
    }

    /** 验收采购单并增加库存。 @author Henfon @date 2026-09-04 */
    @Transactional
    public InventoryPurchaseOrder receive(Long id) {
        InventoryPurchaseOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("INVENTORY_PURCHASE_NOT_FOUND", "采购单不存在");
        if (!Integer.valueOf(STATUS_CREATED).equals(order.getStatus())) throw new BusinessException("INVENTORY_PURCHASE_STATUS_INVALID", "采购单已验收，不能重复入库");
        List<InventoryPurchaseItem> items = itemMapper.selectList(new LambdaQueryWrapper<InventoryPurchaseItem>().eq(InventoryPurchaseItem::getPurchaseOrderId, id));
        for (InventoryPurchaseItem item : items) {
            InventoryStock stock = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>().eq(InventoryStock::getWarehouseId, order.getWarehouseId()).eq(InventoryStock::getSkuId, item.getSkuId()));
            if (stock == null) throw new BusinessException("INVENTORY_PURCHASE_STOCK_NOT_FOUND", "采购SKU库存台账不存在");
            if (stockMapper.inbound(stock.getId(), item.getQuantity()) == 0) throw new BusinessException("INVENTORY_PURCHASE_INBOUND_FAILED", "采购入库失败，请重试");
            item.setReceivedQuantity(item.getQuantity()); itemMapper.updateById(item);
        }
        order.setStatus(STATUS_RECEIVED); order.setReceivedAt(LocalDateTime.now()); orderMapper.updateById(order); return order;
    }
    private String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
