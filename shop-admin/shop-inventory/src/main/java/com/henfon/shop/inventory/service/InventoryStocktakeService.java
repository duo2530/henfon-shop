package com.henfon.shop.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.inventory.dto.InventoryStocktakeCompleteRequest;
import com.henfon.shop.inventory.dto.InventoryStocktakeCancelRequest;
import com.henfon.shop.inventory.dto.InventoryStocktakeCreateRequest;
import com.henfon.shop.inventory.dto.InventoryStocktakeImportRequest;
import com.henfon.shop.inventory.entity.InventoryStock;
import com.henfon.shop.inventory.entity.InventoryStockLog;
import com.henfon.shop.inventory.entity.InventoryStocktake;
import com.henfon.shop.inventory.entity.InventoryStocktakeItem;
import com.henfon.shop.inventory.entity.InventoryWarehouse;
import com.henfon.shop.inventory.mapper.InventoryStockLogMapper;
import com.henfon.shop.inventory.mapper.InventoryStockMapper;
import com.henfon.shop.inventory.mapper.InventoryStocktakeItemMapper;
import com.henfon.shop.inventory.mapper.InventoryStocktakeMapper;
import com.henfon.shop.inventory.mapper.InventoryWarehouseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 库存盘点应用服务。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class InventoryStocktakeService {

    private static final int STATUS_OPEN = 0;
    private static final int STATUS_COMPLETED = 1;
    private static final int STATUS_CANCELLED = 2;

    private final InventoryStocktakeMapper stocktakeMapper;
    private final InventoryStocktakeItemMapper itemMapper;
    private final InventoryWarehouseMapper warehouseMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryStockLogMapper logMapper;

    /**
     * 创建库存盘点服务。
     *
     * @param stocktakeMapper 盘点单数据访问对象
     * @param itemMapper 盘点明细数据访问对象
     * @param warehouseMapper 仓库数据访问对象
     * @param stockMapper 库存台账数据访问对象
     * @param logMapper 库存流水数据访问对象
     * @author Henfon
     * @date 2026-08-31
     */
    public InventoryStocktakeService(InventoryStocktakeMapper stocktakeMapper,
                                     InventoryStocktakeItemMapper itemMapper,
                                     InventoryWarehouseMapper warehouseMapper,
                                     InventoryStockMapper stockMapper,
                                     InventoryStockLogMapper logMapper) {
        this.stocktakeMapper = stocktakeMapper;
        this.itemMapper = itemMapper;
        this.warehouseMapper = warehouseMapper;
        this.stockMapper = stockMapper;
        this.logMapper = logMapper;
    }

    /**
     * 分页查询盘点单。
     *
     * @param warehouseId 仓库ID，可选
     * @param status 盘点状态，可选
     * @param keyword 盘点单号关键字
     * @param current 当前页
     * @param size 页大小
     * @return 盘点单分页数据
     * @author Henfon
     * @date 2026-08-31
     */
    public IPage<InventoryStocktake> page(Long warehouseId, Integer status, String keyword,
                                          long current, long size) {
        // 限制后台分页上限，避免盘点历史记录一次性加载过多。
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return stocktakeMapper.selectPage(new Page<>(safeCurrent, safeSize),
                new LambdaQueryWrapper<InventoryStocktake>()
                        .eq(warehouseId != null, InventoryStocktake::getWarehouseId, warehouseId)
                        .eq(status != null, InventoryStocktake::getStatus, status)
                        .like(normalizedKeyword != null, InventoryStocktake::getTakeNo, normalizedKeyword)
                        .orderByDesc(InventoryStocktake::getCreatedAt));
    }

    /**
     * 查询盘点单明细。
     *
     * @param stocktakeId 盘点单ID
     * @return 盘点明细列表
     * @author Henfon
     * @date 2026-08-31
     */
    public List<InventoryStocktakeItem> listItems(Long stocktakeId) {
        requireStocktake(stocktakeId);
        return itemMapper.selectList(new LambdaQueryWrapper<InventoryStocktakeItem>()
                .eq(InventoryStocktakeItem::getStocktakeId, stocktakeId)
                .orderByAsc(InventoryStocktakeItem::getId));
    }

    /**
     * 创建盘点单并快照指定仓库的库存台账。
     *
     * @param request 创建盘点请求
     * @return 创建后的盘点单
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public InventoryStocktake create(InventoryStocktakeCreateRequest request) {
        InventoryWarehouse warehouse = warehouseMapper.selectById(request.warehouseId());
        if (warehouse == null || !Integer.valueOf(1).equals(warehouse.getStatus())) {
            throw new BusinessException("INVENTORY_STOCKTAKE_WAREHOUSE_INVALID", "盘点仓库不存在或已停用");
        }
        List<InventoryStock> stocks = stockMapper.selectList(new LambdaQueryWrapper<InventoryStock>()
                .eq(InventoryStock::getWarehouseId, request.warehouseId())
                .orderByAsc(InventoryStock::getId));
        if (stocks.isEmpty()) {
            throw new BusinessException("INVENTORY_STOCKTAKE_STOCK_EMPTY", "该仓库暂无库存台账，不能创建盘点单");
        }
        InventoryStocktake stocktake = new InventoryStocktake();
        stocktake.setTakeNo("ST" + IdWorker.getIdStr());
        stocktake.setWarehouseId(request.warehouseId());
        stocktake.setStatus(STATUS_OPEN);
        stocktake.setStartedAt(LocalDateTime.now());
        stocktake.setRemark(trimToNull(request.remark()));
        stocktakeMapper.insert(stocktake);
        // 记录盘点开始时的账面库存，后续完成盘点只提交实盘数量。
        for (InventoryStock stock : stocks) {
            InventoryStocktakeItem item = new InventoryStocktakeItem();
            item.setStocktakeId(stocktake.getId());
            item.setStockId(stock.getId());
            item.setSkuId(stock.getSkuId());
            item.setBookQuantity(stock.getAvailableStock() == null ? 0 : stock.getAvailableStock());
            item.setDifferenceQuantity(0);
            itemMapper.insert(item);
        }
        return stocktake;
    }

    /**
     * 批量导入盘点实盘数量，不改变盘点单状态。
     *
     * @param id 盘点单ID
     * @param request 批量导入请求
     * @return 导入后的盘点明细
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public List<InventoryStocktakeItem> importCounts(Long id, InventoryStocktakeImportRequest request) {
        InventoryStocktake stocktake = requireStocktake(id);
        if (!Integer.valueOf(STATUS_OPEN).equals(stocktake.getStatus())) {
            throw new BusinessException("INVENTORY_STOCKTAKE_STATUS_INVALID", "盘点单已完成，不能导入实盘数量");
        }
        List<InventoryStocktakeItem> items = listItemsWithoutRequire(id);
        Map<Long, InventoryStocktakeItem> itemMap = new HashMap<>();
        for (InventoryStocktakeItem item : items) {
            itemMap.put(item.getId(), item);
        }
        Map<Long, InventoryStocktakeImportRequest.InventoryStocktakeImportItem> submitted = new HashMap<>();
        for (InventoryStocktakeImportRequest.InventoryStocktakeImportItem count : request.items()) {
            if (submitted.put(count.itemId(), count) != null) {
                throw new BusinessException("INVENTORY_STOCKTAKE_IMPORT_DUPLICATE", "导入明细不能重复");
            }
            InventoryStocktakeItem item = itemMap.get(count.itemId());
            if (item == null || !Objects.equals(item.getStocktakeId(), stocktake.getId())) {
                throw new BusinessException("INVENTORY_STOCKTAKE_IMPORT_ITEM_INVALID", "导入明细不属于当前盘点单");
            }
            if (!Objects.equals(item.getSkuId(), count.skuId())) {
                throw new BusinessException("INVENTORY_STOCKTAKE_IMPORT_SKU_INVALID", "导入明细SKU与盘点记录不匹配");
            }
            if (!Objects.equals(item.getVersion(), count.version())) {
                throw new BusinessException("INVENTORY_STOCKTAKE_IMPORT_CONCURRENT", "盘点明细已被其他操作修改，请重新导出");
            }
        }
        for (InventoryStocktakeImportRequest.InventoryStocktakeImportItem count : submitted.values()) {
            InventoryStocktakeItem item = itemMap.get(count.itemId());
            int book = item.getBookQuantity() == null ? 0 : item.getBookQuantity();
            item.setActualQuantity(count.actualQuantity());
            item.setDifferenceQuantity(count.actualQuantity() - book);
            item.setRemark(trimToNull(count.remark()));
            // 更新使用明细版本进行乐观锁保护，重复导入同一版本将被拒绝。
            if (itemMapper.updateById(item) == 0) {
                throw new BusinessException("INVENTORY_STOCKTAKE_IMPORT_CONCURRENT", "盘点明细已被其他操作修改，请重试");
            }
        }
        return listItemsWithoutRequire(id);
    }

    /**
     * 完成盘点并在同一事务内调整库存差异。
     *
     * @param id 盘点单ID
     * @param request 完成盘点请求
     * @return 完成后的盘点单
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public InventoryStocktake complete(Long id, InventoryStocktakeCompleteRequest request) {
        InventoryStocktake stocktake = requireStocktake(id);
        if (!Integer.valueOf(STATUS_OPEN).equals(stocktake.getStatus())) {
            throw new BusinessException("INVENTORY_STOCKTAKE_STATUS_INVALID", "盘点单已完成，不能重复提交");
        }
        List<InventoryStocktakeItem> items = listItemsWithoutRequire(id);
        Map<Long, InventoryStocktakeCompleteRequest.InventoryStocktakeCountItem> submitted = new HashMap<>();
        for (InventoryStocktakeCompleteRequest.InventoryStocktakeCountItem count : request.items()) {
            if (submitted.put(count.itemId(), count) != null) {
                throw new BusinessException("INVENTORY_STOCKTAKE_ITEM_DUPLICATE", "盘点明细不能重复提交");
            }
        }
        if (submitted.size() != items.size() || items.stream().anyMatch(item -> !submitted.containsKey(item.getId()))) {
            throw new BusinessException("INVENTORY_STOCKTAKE_ITEM_INCOMPLETE", "必须提交全部盘点明细");
        }
        for (InventoryStocktakeItem item : items) {
            InventoryStocktakeCompleteRequest.InventoryStocktakeCountItem count = submitted.get(item.getId());
            int book = item.getBookQuantity() == null ? 0 : item.getBookQuantity();
            int actual = count.actualQuantity();
            int difference = actual - book;
            InventoryStock stock = stockMapper.selectById(item.getStockId());
            if (stock == null || !stocktake.getWarehouseId().equals(stock.getWarehouseId())) {
                throw new BusinessException("INVENTORY_STOCKTAKE_STOCK_INVALID", "盘点明细库存台账不存在或仓库不匹配");
            }
            int beforeAvailable = stock.getAvailableStock() == null ? 0 : stock.getAvailableStock();
            // 盘点期间若库存已被订单或人工调整改变，直接拒绝提交，避免把旧快照差异叠加到新库存。
            if (beforeAvailable != book) {
                throw new BusinessException("INVENTORY_STOCKTAKE_CONCURRENT", "库存已发生变化，请重新创建盘点单");
            }
            if (difference != 0 && stockMapper.adjustByStocktake(stock.getId(), difference, stock.getVersion()) == 0) {
                throw new BusinessException("INVENTORY_STOCKTAKE_CONCURRENT", "库存已发生变化，请重新创建盘点单");
            }
            int afterAvailable = beforeAvailable + difference;
            item.setActualQuantity(actual);
            item.setDifferenceQuantity(difference);
            item.setRemark(trimToNull(count.remark()));
            if (itemMapper.updateById(item) == 0) {
                throw new BusinessException("INVENTORY_STOCKTAKE_CONCURRENT", "盘点明细已被其他操作修改");
            }
            if (difference != 0) {
                saveStocktakeLog(stock, stocktake.getTakeNo(), difference, beforeAvailable, afterAvailable);
            }
        }
        stocktake.setStatus(STATUS_COMPLETED);
        stocktake.setCompletedAt(LocalDateTime.now());
        if (StringUtils.hasText(request.remark())) {
            stocktake.setRemark(request.remark().trim());
        }
        if (stocktakeMapper.updateById(stocktake) == 0) {
            throw new BusinessException("INVENTORY_STOCKTAKE_CONCURRENT", "盘点单已被其他操作修改");
        }
        return stocktake;
    }

    /**
     * 取消尚未完成的库存盘点单。
     *
     * @param id 盘点单ID
     * @param request 取消盘点请求
     * @return 取消后的盘点单
     * @author Henfon
     * @date 2026-09-04
     */
    @Transactional
    public InventoryStocktake cancel(Long id, InventoryStocktakeCancelRequest request) {
        InventoryStocktake stocktake = requireStocktake(id);
        if (!Integer.valueOf(STATUS_OPEN).equals(stocktake.getStatus())) {
            throw new BusinessException("INVENTORY_STOCKTAKE_STATUS_INVALID", "仅进行中的盘点单可以取消");
        }
        // 取消只变更盘点单状态，不改动库存台账，避免产生虚假的盘盈盘亏流水。
        stocktake.setStatus(STATUS_CANCELLED);
        stocktake.setCompletedAt(LocalDateTime.now());
        if (request != null && StringUtils.hasText(request.remark())) {
            stocktake.setRemark(request.remark().trim());
        }
        if (stocktakeMapper.updateById(stocktake) == 0) {
            throw new BusinessException("INVENTORY_STOCKTAKE_CONCURRENT", "盘点单已被其他操作修改");
        }
        return stocktake;
    }

    /**
     * 查询盘点单，不存在时抛出业务异常。
     *
     * @param id 盘点单ID
     * @return 盘点单实体
     * @author Henfon
     * @date 2026-08-31
     */
    private InventoryStocktake requireStocktake(Long id) {
        // 统一将空ID和逻辑删除盘点单转换为业务异常。
        InventoryStocktake stocktake = id == null ? null : stocktakeMapper.selectById(id);
        if (stocktake == null) {
            throw new BusinessException("INVENTORY_STOCKTAKE_NOT_FOUND", "盘点单不存在");
        }
        return stocktake;
    }

    /**
     * 查询盘点明细，不重复校验盘点单。
     *
     * @param stocktakeId 盘点单ID
     * @return 盘点明细列表
     * @author Henfon
     * @date 2026-08-31
     */
    private List<InventoryStocktakeItem> listItemsWithoutRequire(Long stocktakeId) {
        return itemMapper.selectList(new LambdaQueryWrapper<InventoryStocktakeItem>()
                .eq(InventoryStocktakeItem::getStocktakeId, stocktakeId)
                .orderByAsc(InventoryStocktakeItem::getId));
    }

    /**
     * 记录盘点库存调整流水。
     *
     * @param stock 库存台账
     * @param takeNo 盘点单号
     * @param difference 差异数量
     * @param beforeAvailable 调整前可用库存
     * @param afterAvailable 调整后可用库存
     * @author Henfon
     * @date 2026-08-31
     */
    private void saveStocktakeLog(InventoryStock stock, String takeNo, int difference,
                                  int beforeAvailable, int afterAvailable) {
        // 盘点差异使用独立业务类型，方便审计和后续报表聚合。
        InventoryStockLog log = new InventoryStockLog();
        log.setStockId(stock.getId());
        log.setSkuId(stock.getSkuId());
        log.setBizType("STOCKTAKE");
        log.setBizNo(takeNo);
        log.setChangeQuantity(difference);
        log.setBeforeAvailable(beforeAvailable);
        log.setAfterAvailable(afterAvailable);
        log.setBeforeLocked(stock.getLockedStock() == null ? 0 : stock.getLockedStock());
        log.setAfterLocked(stock.getLockedStock() == null ? 0 : stock.getLockedStock());
        log.setRemark("库存盘点差异调整");
        logMapper.insert(log);
    }

    /**
     * 清理可选文本。
     *
     * @param value 原始文本
     * @return 清理后的文本或空值
     * @author Henfon
     * @date 2026-08-31
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
