package com.henfon.shop.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.inventory.dto.InventorySupplierStockSaveRequest;
import com.henfon.shop.inventory.entity.InventorySupplier;
import com.henfon.shop.inventory.entity.InventorySupplierStock;
import com.henfon.shop.inventory.mapper.InventorySupplierMapper;
import com.henfon.shop.inventory.mapper.InventorySupplierStockMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 供应商SKU供货关系应用服务。 @author Henfon @date 2026-09-04 */
@Service
public class InventorySupplierStockService {
    private final InventorySupplierStockMapper stockMapper;
    private final InventorySupplierMapper supplierMapper;

    /** 创建供货关系服务。 @author Henfon @date 2026-09-04 */
    public InventorySupplierStockService(InventorySupplierStockMapper stockMapper, InventorySupplierMapper supplierMapper) {
        this.stockMapper = stockMapper;
        this.supplierMapper = supplierMapper;
    }

    /** 分页查询供应商供货SKU。 @author Henfon @date 2026-09-04 */
    public IPage<InventorySupplierStock> page(Long supplierId, Long skuId, long current, long size) {
        // 供货关系按更新时间倒序，限制单页大小防止批量加载。
        return stockMapper.selectPage(new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 200)),
                new LambdaQueryWrapper<InventorySupplierStock>().eq(InventorySupplierStock::getSupplierId, supplierId)
                        .eq(skuId != null, InventorySupplierStock::getSkuId, skuId)
                        .orderByDesc(InventorySupplierStock::getUpdatedAt));
    }

    /** 新增或编辑供应商SKU供货关系。 @author Henfon @date 2026-09-04 */
    @Transactional
    public Long save(Long supplierId, InventorySupplierStockSaveRequest request) {
        InventorySupplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null || !Integer.valueOf(1).equals(supplier.getStatus())) {
            throw new BusinessException("INVENTORY_SUPPLIER_INVALID", "供应商不存在或已停用");
        }
        InventorySupplierStock entity = request.id() == null ? new InventorySupplierStock() : require(request.id());
        if (request.id() != null && !supplierId.equals(entity.getSupplierId())) {
            throw new BusinessException("INVENTORY_SUPPLIER_STOCK_MISMATCH", "供货关系不属于当前供应商");
        }
        InventorySupplierStock duplicate = stockMapper.selectOne(new LambdaQueryWrapper<InventorySupplierStock>()
                .eq(InventorySupplierStock::getSupplierId, supplierId)
                .eq(InventorySupplierStock::getSkuId, request.skuId())
                .ne(request.id() != null, InventorySupplierStock::getId, request.id()));
        if (duplicate != null) throw new BusinessException("INVENTORY_SUPPLIER_STOCK_EXISTS", "该SKU已绑定当前供应商");
        if (request.id() != null && request.version() != null && !request.version().equals(entity.getVersion())) {
            throw new BusinessException("INVENTORY_SUPPLIER_STOCK_CONCURRENT", "供货关系已被修改，请刷新后重试");
        }
        entity.setSupplierId(supplierId);
        entity.setProductId(request.productId());
        entity.setSkuId(request.skuId());
        entity.setSupplyPrice(request.supplyPrice());
        entity.setMinOrderQuantity(request.minOrderQuantity());
        entity.setStatus(request.status());
        entity.setRemark(StringUtils.hasText(request.remark()) ? request.remark().trim() : null);
        if (entity.getId() == null) stockMapper.insert(entity);
        else if (stockMapper.updateById(entity) == 0) throw new BusinessException("INVENTORY_SUPPLIER_STOCK_CONCURRENT", "供货关系已被修改，请刷新后重试");
        return entity.getId();
    }

    /** 删除供应商SKU供货关系。 @author Henfon @date 2026-09-04 */
    @Transactional
    public void delete(Long supplierId, Long id) {
        InventorySupplierStock entity = require(id);
        if (!supplierId.equals(entity.getSupplierId())) throw new BusinessException("INVENTORY_SUPPLIER_STOCK_MISMATCH", "供货关系不属于当前供应商");
        if (stockMapper.deleteById(id) == 0) throw new BusinessException("INVENTORY_SUPPLIER_STOCK_NOT_FOUND", "供货关系不存在");
    }

    /** 查询供货关系，不存在时抛出业务异常。 @author Henfon @date 2026-09-04 */
    private InventorySupplierStock require(Long id) {
        // 统一处理空ID和逻辑删除记录，避免返回空对象。
        InventorySupplierStock entity = id == null ? null : stockMapper.selectById(id);
        if (entity == null) throw new BusinessException("INVENTORY_SUPPLIER_STOCK_NOT_FOUND", "供货关系不存在");
        return entity;
    }
}
