package com.henfon.shop.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.inventory.dto.InventoryWarehouseSaveRequest;
import com.henfon.shop.inventory.entity.InventoryStock;
import com.henfon.shop.inventory.entity.InventoryWarehouse;
import com.henfon.shop.inventory.mapper.InventoryStockMapper;
import com.henfon.shop.inventory.mapper.InventoryWarehouseMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 仓库基础管理应用服务。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class InventoryWarehouseService {

    private final InventoryWarehouseMapper warehouseMapper;
    private final InventoryStockMapper stockMapper;

    /**
     * 创建仓库管理服务。
     *
     * @param warehouseMapper 仓库数据访问对象
     * @param stockMapper 库存台账数据访问对象
     * @author Henfon
     * @date 2026-08-31
     */
    public InventoryWarehouseService(InventoryWarehouseMapper warehouseMapper, InventoryStockMapper stockMapper) {
        this.warehouseMapper = warehouseMapper;
        this.stockMapper = stockMapper;
    }

    /**
     * 分页查询未删除仓库。
     *
     * @param keyword 仓库编码或名称关键字
     * @param status 状态，可选
     * @param current 当前页
     * @param size 页大小
     * @return 仓库分页数据
     * @author Henfon
     * @date 2026-08-31
     */
    public IPage<InventoryWarehouse> page(String keyword, Integer status, long current, long size) {
        // 限制分页上限，避免后台查询一次性读取大量仓库记录。
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return warehouseMapper.selectPage(new Page<>(safeCurrent, safeSize),
                new LambdaQueryWrapper<InventoryWarehouse>()
                        .and(normalizedKeyword != null, wrapper -> wrapper
                                .like(InventoryWarehouse::getWarehouseCode, normalizedKeyword)
                                .or()
                                .like(InventoryWarehouse::getWarehouseName, normalizedKeyword))
                        .eq(status != null, InventoryWarehouse::getStatus, status)
                        .orderByDesc(InventoryWarehouse::getIsDefault)
                        .orderByDesc(InventoryWarehouse::getUpdatedAt)
                        .orderByAsc(InventoryWarehouse::getId));
    }

    /**
     * 查询启用仓库列表，供库存选择和内部业务使用。
     *
     * @return 启用仓库列表
     * @author Henfon
     * @date 2026-08-31
     */
    public java.util.List<InventoryWarehouse> listEnabled() {
        // 默认仓库优先返回，确保库存业务优先使用稳定的仓库配置。
        return warehouseMapper.selectList(new LambdaQueryWrapper<InventoryWarehouse>()
                .eq(InventoryWarehouse::getStatus, 1)
                .orderByDesc(InventoryWarehouse::getIsDefault)
                .orderByAsc(InventoryWarehouse::getId));
    }

    /**
     * 新增或编辑仓库。
     *
     * @param request 仓库保存请求
     * @return 保存后的仓库ID
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public Long save(InventoryWarehouseSaveRequest request) {
        String code = request.warehouseCode().trim().toUpperCase();
        String name = request.warehouseName().trim();
        if (request.status() == 0 && request.isDefault() == 1) {
            throw new BusinessException("INVENTORY_WAREHOUSE_DEFAULT_DISABLED", "默认仓库必须保持启用");
        }
        InventoryWarehouse warehouse = request.id() == null ? new InventoryWarehouse() : requireWarehouse(request.id());
        InventoryWarehouse duplicate = warehouseMapper.selectOne(new LambdaQueryWrapper<InventoryWarehouse>()
                .eq(InventoryWarehouse::getWarehouseCode, code)
                .ne(request.id() != null, InventoryWarehouse::getId, request.id()));
        if (duplicate != null) {
            throw new BusinessException("INVENTORY_WAREHOUSE_CODE_EXISTS", "仓库编码已存在");
        }
        if (request.id() != null && request.version() != null
                && !request.version().equals(warehouse.getVersion())) {
            throw new BusinessException("INVENTORY_WAREHOUSE_CONCURRENT", "仓库已被其他操作修改，请刷新后重试");
        }
        if (request.isDefault() == 1) {
            // 设置新默认仓库前先清除旧默认标记，保证业务层始终只有一个默认仓库。
            clearDefault(request.id());
        } else if (!hasEnabledDefaultWarehouse(request.id())) {
            throw new BusinessException("INVENTORY_WAREHOUSE_DEFAULT_REQUIRED", "系统至少需要保留一个默认仓库");
        }
        warehouse.setWarehouseCode(code);
        warehouse.setWarehouseName(name);
        warehouse.setStatus(request.status());
        warehouse.setIsDefault(request.isDefault());
        warehouse.setRemark(trimToNull(request.remark()));
        try {
            if (warehouse.getId() == null) {
                warehouseMapper.insert(warehouse);
            } else if (warehouseMapper.updateById(warehouse) == 0) {
                throw new BusinessException("INVENTORY_WAREHOUSE_CONCURRENT", "仓库已被其他操作修改，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("INVENTORY_WAREHOUSE_CODE_EXISTS", "仓库编码已存在");
        }
        return warehouse.getId();
    }

    /**
     * 修改仓库启停状态。
     *
     * @param id 仓库ID
     * @param status 目标状态，1启用、0停用
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        // 状态值只允许启用和停用，避免把未知状态写入库存业务表。
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("INVENTORY_WAREHOUSE_STATUS_INVALID", "仓库状态必须为 0 或 1");
        }
        InventoryWarehouse warehouse = requireWarehouse(id);
        if (status == 0 && warehouse.getIsDefault() != null && warehouse.getIsDefault() == 1) {
            throw new BusinessException("INVENTORY_WAREHOUSE_DEFAULT_DISABLED", "默认仓库不能停用");
        }
        warehouse.setStatus(status);
        try {
            if (warehouseMapper.updateById(warehouse) == 0) {
                throw new BusinessException("INVENTORY_WAREHOUSE_CONCURRENT", "仓库已被其他操作修改，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            // 数据库唯一约束是并发场景下的最后一道默认仓库保护。
            throw new BusinessException("INVENTORY_WAREHOUSE_DEFAULT_EXISTS", "已有其他默认仓库，请刷新后重试");
        }
    }

    /**
     * 逻辑删除仓库，并校验默认仓库及库存台账引用。
     *
     * @param id 仓库ID
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public void delete(Long id) {
        InventoryWarehouse warehouse = requireWarehouse(id);
        // 默认仓库承载订单库存预占，删除前必须先切换默认仓库。
        if (warehouse.getIsDefault() != null && warehouse.getIsDefault() == 1) {
            throw new BusinessException("INVENTORY_WAREHOUSE_DEFAULT_DELETE", "默认仓库不能删除");
        }
        long stockCount = stockMapper.selectCount(new LambdaQueryWrapper<InventoryStock>()
                .eq(InventoryStock::getWarehouseId, id));
        if (stockCount > 0) {
            throw new BusinessException("INVENTORY_WAREHOUSE_IN_USE", "仓库存在库存台账，不能删除");
        }
        if (warehouseMapper.deleteById(id) == 0) {
            throw new BusinessException("INVENTORY_WAREHOUSE_NOT_FOUND", "仓库不存在");
        }
    }

    /**
     * 查询仓库，不存在时抛出统一业务异常。
     *
     * @param id 仓库ID
     * @return 仓库实体
     * @author Henfon
     * @date 2026-08-31
     */
    private InventoryWarehouse requireWarehouse(Long id) {
        // 统一将空ID和逻辑删除记录转换为业务异常，避免控制器返回空对象。
        if (id == null) {
            throw new BusinessException("INVENTORY_WAREHOUSE_NOT_FOUND", "仓库不存在");
        }
        InventoryWarehouse warehouse = warehouseMapper.selectById(id);
        if (warehouse == null) {
            throw new BusinessException("INVENTORY_WAREHOUSE_NOT_FOUND", "仓库不存在");
        }
        return warehouse;
    }

    /**
     * 清除当前默认仓库标识。
     *
     * @param excludedId 本次保存仓库ID，可为空
     * @author Henfon
     * @date 2026-08-31
     */
    private void clearDefault(Long excludedId) {
        LambdaQueryWrapper<InventoryWarehouse> query = new LambdaQueryWrapper<InventoryWarehouse>()
                .eq(InventoryWarehouse::getIsDefault, 1);
        if (excludedId != null) {
            query.ne(InventoryWarehouse::getId, excludedId);
        }
        for (InventoryWarehouse item : warehouseMapper.selectList(query)) {
            item.setIsDefault(0);
            warehouseMapper.updateById(item);
        }
    }

    /**
     * 判断是否存在其他启用默认仓库。
     *
     * @param excludedId 排除仓库ID
     * @return 是否存在其他启用仓库
     * @author Henfon
     * @date 2026-08-31
     */
    private boolean hasEnabledDefaultWarehouse(Long excludedId) {
        // 排除当前编辑记录，用于校验保存非默认仓库时系统仍有默认仓库。
        LambdaQueryWrapper<InventoryWarehouse> query = new LambdaQueryWrapper<InventoryWarehouse>()
                .eq(InventoryWarehouse::getStatus, 1)
                .eq(InventoryWarehouse::getIsDefault, 1);
        if (excludedId != null) {
            query.ne(InventoryWarehouse::getId, excludedId);
        }
        return warehouseMapper.selectCount(query) > 0;
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
