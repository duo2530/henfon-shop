package com.henfon.shop.inventory.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.inventory.dto.InventoryReservationItem;
import com.henfon.shop.inventory.dto.InventoryStockSaveRequest;
import com.henfon.shop.inventory.entity.InventoryStock;
import com.henfon.shop.inventory.entity.InventoryStockLock;
import com.henfon.shop.inventory.entity.InventoryStockLog;
import com.henfon.shop.inventory.entity.InventoryWarehouse;
import com.henfon.shop.inventory.mapper.InventoryStockLockMapper;
import com.henfon.shop.inventory.mapper.InventoryStockLogMapper;
import com.henfon.shop.inventory.mapper.InventoryStockMapper;
import com.henfon.shop.inventory.mapper.InventoryWarehouseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存台账应用服务，负责库存预占、释放和人工调整。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Service
public class InventoryStockService {

    private static final int LOCKED = 0;
    private static final int RELEASED = 1;
    private static final int DEDUCTED = 2;

    private final InventoryWarehouseMapper warehouseMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryStockLockMapper lockMapper;
    private final InventoryStockLogMapper logMapper;

    /**
     * 创建库存服务。
     *
     * @param warehouseMapper 仓库数据访问对象
     * @param stockMapper 库存数据访问对象
     * @param lockMapper 锁定流水数据访问对象
     * @param logMapper 变更流水数据访问对象
     * @author Henfon
     * @date 2026-08-30
     */
    public InventoryStockService(InventoryWarehouseMapper warehouseMapper, InventoryStockMapper stockMapper,
                                 InventoryStockLockMapper lockMapper, InventoryStockLogMapper logMapper) {
        this.warehouseMapper = warehouseMapper;
        this.stockMapper = stockMapper;
        this.lockMapper = lockMapper;
        this.logMapper = logMapper;
    }

    /**
     * 分页查询库存台账。
     *
     * @param skuId SKU ID，可选
     * @param current 当前页
     * @param size 页大小
     * @return 库存分页结果
     * @author Henfon
     * @date 2026-08-30
     */
    public IPage<InventoryStock> page(Long skuId, long current, long size) {
        // 后台分页统一限制单页大小，避免库存台账被一次性加载。
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        return stockMapper.selectPage(new Page<>(safeCurrent, safeSize),
                new LambdaQueryWrapper<InventoryStock>()
                        .eq(skuId != null, InventoryStock::getSkuId, skuId)
                        .orderByDesc(InventoryStock::getUpdatedAt));
    }

    /**
     * 为订单预占库存。
     *
     * @param orderId 订单ID
     * @param orderNo 订单号
     * @param items 订单商品明细
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void reserve(Long orderId, String orderNo, List<InventoryReservationItem> items) {
        InventoryWarehouse warehouse = defaultWarehouse();
        // 合并同一订单中的重复 SKU，避免多行商品导致只锁定第一行数量。
        Map<Long, InventoryReservationItem> mergedItems = new LinkedHashMap<>();
        for (InventoryReservationItem item : items) {
            if (item.skuId() == null) {
                continue;
            }
            InventoryReservationItem previous = mergedItems.get(item.skuId());
            mergedItems.put(item.skuId(), previous == null
                    ? item
                    : new InventoryReservationItem(item.productId(), item.skuId(), previous.quantity() + item.quantity()));
        }
        for (InventoryReservationItem item : mergedItems.values()) {
            if (item.quantity() <= 0) {
                throw new BusinessException("INVENTORY_QUANTITY_INVALID", "库存数量必须大于0");
            }
            InventoryStockLock existed = lockMapper.selectOne(new LambdaQueryWrapper<InventoryStockLock>()
                    .eq(InventoryStockLock::getOrderId, orderId)
                    .eq(InventoryStockLock::getSkuId, item.skuId())
                    .eq(InventoryStockLock::getStatus, LOCKED));
            if (existed != null) {
                continue;
            }
            InventoryStock stock = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                    .eq(InventoryStock::getWarehouseId, warehouse.getId())
                    .eq(InventoryStock::getSkuId, item.skuId()));
            if (stock == null || stock.getAvailableStock() == null) {
                throw new BusinessException("INVENTORY_STOCK_NOT_FOUND", "SKU库存台账不存在");
            }
            int beforeAvailable = stock.getAvailableStock();
            int beforeLocked = stock.getLockedStock();
            if (stockMapper.reserve(stock.getId(), item.quantity()) == 0) {
                throw new BusinessException("INVENTORY_STOCK_NOT_ENOUGH", "商品库存不足");
            }
            InventoryStockLock lock = new InventoryStockLock();
            lock.setLockNo("L" + IdWorker.getIdStr());
            lock.setOrderId(orderId);
            lock.setOrderNo(orderNo);
            lock.setStockId(stock.getId());
            lock.setSkuId(item.skuId());
            lock.setQuantity(item.quantity());
            lock.setStatus(LOCKED);
            lock.setExpireAt(LocalDateTime.now().plusMinutes(30));
            lockMapper.insert(lock);
            InventoryStock afterStock = stockMapper.selectById(stock.getId());
            saveLog(stock, "RESERVE", orderNo, -item.quantity(), beforeAvailable,
                    afterStock.getAvailableStock(), beforeLocked, afterStock.getLockedStock(), "订单库存预占");
        }
    }

    /**
     * 释放订单已预占库存。
     *
     * @param orderId 订单ID
     * @param orderNo 订单号
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void release(Long orderId, String orderNo) {
        List<InventoryStockLock> locks = lockMapper.selectList(new LambdaQueryWrapper<InventoryStockLock>()
                .eq(InventoryStockLock::getOrderId, orderId)
                .eq(InventoryStockLock::getStatus, LOCKED));
        for (InventoryStockLock lock : locks) {
            InventoryStock stock = stockMapper.selectById(lock.getStockId());
            if (stock == null || stockMapper.release(stock.getId(), lock.getQuantity()) == 0) {
                throw new BusinessException("INVENTORY_RELEASE_FAILED", "库存释放失败，请稍后重试");
            }
            int beforeAvailable = stock.getAvailableStock();
            int beforeLocked = stock.getLockedStock();
            InventoryStock afterStock = stockMapper.selectById(stock.getId());
            lock.setStatus(RELEASED);
            lock.setReleasedAt(LocalDateTime.now());
            lockMapper.updateById(lock);
            saveLog(stock, "RELEASE", orderNo, lock.getQuantity(), beforeAvailable,
                    afterStock.getAvailableStock(), beforeLocked, afterStock.getLockedStock(), "订单取消释放库存");
        }
    }

    /**
     * 订单发货时扣减已锁定库存。
     *
     * @param orderId 订单ID
     * @param orderNo 订单号
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void deduct(Long orderId, String orderNo) {
        List<InventoryStockLock> locks = lockMapper.selectList(new LambdaQueryWrapper<InventoryStockLock>()
                .eq(InventoryStockLock::getOrderId, orderId)
                .eq(InventoryStockLock::getStatus, LOCKED));
        for (InventoryStockLock lock : locks) {
            InventoryStock stock = stockMapper.selectById(lock.getStockId());
            if (stock == null || stockMapper.deduct(stock.getId(), lock.getQuantity()) == 0) {
                throw new BusinessException("INVENTORY_DEDUCT_FAILED", "发货扣减库存失败，请刷新后重试");
            }
            int beforeAvailable = stock.getAvailableStock();
            int beforeLocked = stock.getLockedStock();
            InventoryStock afterStock = stockMapper.selectById(stock.getId());
            lock.setStatus(DEDUCTED);
            lock.setDeductedAt(LocalDateTime.now());
            lockMapper.updateById(lock);
            saveLog(stock, "DEDUCT", orderNo, 0, beforeAvailable, afterStock.getAvailableStock(),
                    beforeLocked, afterStock.getLockedStock(), "订单发货扣减锁定库存");
        }
    }

    /**
     * 人工调整库存台账。
     *
     * @param stockId 台账ID
     * @param changeQuantity 调整数量
     * @param remark 调整原因
     * @return 调整后的库存台账
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public InventoryStock adjust(Long stockId, int changeQuantity, String remark) {
        InventoryStock stock = stockMapper.selectById(stockId);
        if (stock == null) {
            throw new BusinessException("INVENTORY_STOCK_NOT_FOUND", "库存台账不存在");
        }
        int before = stock.getAvailableStock();
        int after = before + changeQuantity;
        if (after < 0) {
            throw new BusinessException("INVENTORY_STOCK_NOT_ENOUGH", "可用库存不足，不能出库");
        }
        stock.setAvailableStock(after);
        if (stockMapper.updateById(stock) == 0) {
            throw new BusinessException("INVENTORY_CONCURRENT_UPDATE", "库存已被其他操作修改，请刷新后重试");
        }
        saveLog(stock, "ADJUST", "ADJUST-" + stockId, changeQuantity, before, after,
                stock.getLockedStock(), stock.getLockedStock(), remark);
        return stock;
    }

    /**
     * 初始化或更新指定仓库的 SKU 库存台账。
     *
     * @param request 台账请求
     * @return 保存后的库存台账
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public InventoryStock save(InventoryStockSaveRequest request) {
        Long warehouseId = request.warehouseId();
        if (warehouseId == null) {
            warehouseId = defaultWarehouse().getId();
        }
        InventoryStock stock = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                .eq(InventoryStock::getWarehouseId, warehouseId)
                .eq(InventoryStock::getSkuId, request.skuId()));
        if (stock == null) {
            stock = new InventoryStock();
            stock.setWarehouseId(warehouseId);
            stock.setProductId(request.productId());
            stock.setSkuId(request.skuId());
            stock.setAvailableStock(request.availableStock());
            stock.setLockedStock(0);
            stock.setSoldStock(0L);
            stock.setSafetyStock(request.safetyStock());
            stock.setRemark(request.remark());
            stockMapper.insert(stock);
            saveLog(stock, "ADJUST", "INIT-" + stock.getId(), request.availableStock(), 0,
                    request.availableStock(), 0, 0, "初始化库存台账");
            return stock;
        }
        // 编辑台账只允许修改安全库存，库存数量通过调整接口变更，避免覆盖锁定库存。
        stock.setProductId(request.productId());
        stock.setSafetyStock(request.safetyStock());
        stock.setRemark(request.remark());
        if (stockMapper.updateById(stock) == 0) {
            throw new BusinessException("INVENTORY_CONCURRENT_UPDATE", "库存已被其他操作修改，请刷新后重试");
        }
        return stock;
    }

    /**
     * 查询默认启用仓库。
     *
     * @return 默认仓库
     * @author Henfon
     * @date 2026-08-30
     */
    private InventoryWarehouse defaultWarehouse() {
        InventoryWarehouse warehouse = warehouseMapper.selectOne(new LambdaQueryWrapper<InventoryWarehouse>()
                .eq(InventoryWarehouse::getStatus, 1)
                .eq(InventoryWarehouse::getIsDefault, 1));
        if (warehouse == null) {
            throw new BusinessException("INVENTORY_WAREHOUSE_NOT_FOUND", "默认仓库不存在");
        }
        return warehouse;
    }

    /**
     * 保存库存变更流水。
     *
     * @param stock 库存台账
     * @param bizType 业务类型
     * @param bizNo 业务单号
     * @param changeQuantity 变化数量
     * @param beforeAvailable 变更前可用库存
     * @param afterAvailable 变更后可用库存
     * @param beforeLocked 变更前锁定库存
     * @param afterLocked 变更后锁定库存
     * @param remark 备注
     * @author Henfon
     * @date 2026-08-30
     */
    private void saveLog(InventoryStock stock, String bizType, String bizNo, int changeQuantity,
                         int beforeAvailable, int afterAvailable, int beforeLocked, int afterLocked, String remark) {
        InventoryStockLog log = new InventoryStockLog();
        log.setStockId(stock.getId());
        log.setSkuId(stock.getSkuId());
        log.setBizType(bizType);
        log.setBizNo(bizNo);
        log.setChangeQuantity(changeQuantity);
        log.setBeforeAvailable(beforeAvailable);
        log.setAfterAvailable(afterAvailable);
        log.setBeforeLocked(beforeLocked);
        log.setAfterLocked(afterLocked);
        log.setRemark(remark);
        logMapper.insert(log);
    }
}
