package com.henfon.shop.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.inventory.dto.InventorySupplierSaveRequest;
import com.henfon.shop.inventory.entity.InventorySupplier;
import com.henfon.shop.inventory.mapper.InventorySupplierMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 库存供应商基础管理应用服务。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class InventorySupplierService {

    private final InventorySupplierMapper supplierMapper;

    /**
     * 创建供应商管理服务。
     *
     * @param supplierMapper 供应商数据访问对象
     * @author Henfon
     * @date 2026-08-31
     */
    public InventorySupplierService(InventorySupplierMapper supplierMapper) {
        this.supplierMapper = supplierMapper;
    }

    /**
     * 分页查询供应商。
     *
     * @param keyword 供应商编码、名称或联系人关键字
     * @param status 状态，可选
     * @param current 当前页
     * @param size 页大小
     * @return 供应商分页数据
     * @author Henfon
     * @date 2026-08-31
     */
    public IPage<InventorySupplier> page(String keyword, Integer status, long current, long size) {
        // 统一限制后台分页上限，避免供应商数据量增长后一次性加载过多记录。
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return supplierMapper.selectPage(new Page<>(safeCurrent, safeSize),
                new LambdaQueryWrapper<InventorySupplier>()
                        .and(normalizedKeyword != null, wrapper -> wrapper
                                .like(InventorySupplier::getSupplierCode, normalizedKeyword)
                                .or()
                                .like(InventorySupplier::getSupplierName, normalizedKeyword)
                                .or()
                                .like(InventorySupplier::getContactName, normalizedKeyword))
                        .eq(status != null, InventorySupplier::getStatus, status)
                        .orderByDesc(InventorySupplier::getUpdatedAt)
                        .orderByAsc(InventorySupplier::getId));
    }

    /**
     * 新增或编辑供应商。
     *
     * @param request 供应商保存请求
     * @return 保存后的供应商ID
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public Long save(InventorySupplierSaveRequest request) {
        String code = request.supplierCode().trim().toUpperCase();
        String name = request.supplierName().trim();
        InventorySupplier supplier = request.id() == null ? new InventorySupplier() : requireSupplier(request.id());
        InventorySupplier duplicate = supplierMapper.selectOne(new LambdaQueryWrapper<InventorySupplier>()
                .eq(InventorySupplier::getSupplierCode, code)
                .ne(request.id() != null, InventorySupplier::getId, request.id()));
        if (duplicate != null) {
            throw new BusinessException("INVENTORY_SUPPLIER_CODE_EXISTS", "供应商编码已存在");
        }
        if (request.id() != null && request.version() != null
                && !request.version().equals(supplier.getVersion())) {
            throw new BusinessException("INVENTORY_SUPPLIER_CONCURRENT", "供应商已被其他操作修改，请刷新后重试");
        }
        supplier.setSupplierCode(code);
        supplier.setSupplierName(name);
        supplier.setContactName(trimToNull(request.contactName()));
        supplier.setContactPhone(trimToNull(request.contactPhone()));
        supplier.setAddress(trimToNull(request.address()));
        supplier.setStatus(request.status());
        supplier.setRemark(trimToNull(request.remark()));
        try {
            if (supplier.getId() == null) {
                supplierMapper.insert(supplier);
            } else if (supplierMapper.updateById(supplier) == 0) {
                throw new BusinessException("INVENTORY_SUPPLIER_CONCURRENT", "供应商已被其他操作修改，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            // 数据库唯一索引是并发保存时的最后一道编码保护。
            throw new BusinessException("INVENTORY_SUPPLIER_CODE_EXISTS", "供应商编码已存在");
        }
        return supplier.getId();
    }

    /**
     * 修改供应商启停状态。
     *
     * @param id 供应商ID
     * @param status 目标状态，1启用、0停用
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        // 限制状态枚举，避免将未知值写入供应商表。
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("INVENTORY_SUPPLIER_STATUS_INVALID", "供应商状态必须为 0 或 1");
        }
        InventorySupplier supplier = requireSupplier(id);
        supplier.setStatus(status);
        if (supplierMapper.updateById(supplier) == 0) {
            throw new BusinessException("INVENTORY_SUPPLIER_CONCURRENT", "供应商已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 逻辑删除供应商。
     *
     * @param id 供应商ID
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public void delete(Long id) {
        InventorySupplier supplier = requireSupplier(id);
        // 当前工程尚无采购单或库存台账的供应商外键，后续增加关联表后在此补充引用校验。
        if (supplierMapper.deleteById(supplier.getId()) == 0) {
            throw new BusinessException("INVENTORY_SUPPLIER_NOT_FOUND", "供应商不存在");
        }
    }

    /**
     * 查询供应商，不存在时抛出统一业务异常。
     *
     * @param id 供应商ID
     * @return 供应商实体
     * @author Henfon
     * @date 2026-08-31
     */
    private InventorySupplier requireSupplier(Long id) {
        // 将空ID或逻辑删除记录统一转换为业务异常，避免控制器返回空数据。
        InventorySupplier supplier = id == null ? null : supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BusinessException("INVENTORY_SUPPLIER_NOT_FOUND", "供应商不存在");
        }
        return supplier;
    }

    /**
     * 清理可选文本。
     *
     * @param value 原始文本
     * @return 去除首尾空格后的文本或空值
     * @author Henfon
     * @date 2026-08-31
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
