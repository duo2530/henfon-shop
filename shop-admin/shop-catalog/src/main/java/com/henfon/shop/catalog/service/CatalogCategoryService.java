package com.henfon.shop.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.catalog.entity.CatalogCategory;
import com.henfon.shop.catalog.dto.CatalogCategorySaveRequest;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.mapper.CatalogCategoryMapper;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 商品类目应用服务。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class CatalogCategoryService {

    private final CatalogCategoryMapper catalogCategoryMapper;
    private final CatalogProductMapper catalogProductMapper;

    /**
     * 创建商品类目服务。
     *
     * @param catalogCategoryMapper 类目数据访问对象
     * @author Henfon
     * @date 2026-08-29
     */
    public CatalogCategoryService(CatalogCategoryMapper catalogCategoryMapper, CatalogProductMapper catalogProductMapper) {
        this.catalogCategoryMapper = catalogCategoryMapper;
        this.catalogProductMapper = catalogProductMapper;
    }

    /**
     * 查询启用类目列表。
     *
     * @return 类目列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<CatalogCategory> listEnabled() {
        // 商品编辑页只展示启用类目，停用类目仍保留在后台数据库中。
        return catalogCategoryMapper.selectList(new LambdaQueryWrapper<CatalogCategory>()
                .eq(CatalogCategory::getStatus, 1)
                .orderByAsc(CatalogCategory::getParentId)
                .orderByAsc(CatalogCategory::getSortNo));
    }

    /**
     * 查询后台类目树数据。
     *
     * @return 全部未删除类目
     * @author Henfon
     * @date 2026-08-30
     */
    public List<CatalogCategory> listAdmin() {
        return catalogCategoryMapper.selectList(new LambdaQueryWrapper<CatalogCategory>()
                .orderByAsc(CatalogCategory::getParentId)
                .orderByAsc(CatalogCategory::getSortNo)
                .orderByAsc(CatalogCategory::getId));
    }

    /**
     * 保存或更新商品类目。
     *
     * @param request 类目保存请求
     * @return 类目ID
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public Long save(CatalogCategorySaveRequest request) {
        long parentId = request.parentId() == null ? 0L : request.parentId();
        if (request.id() != null && request.id().equals(parentId)) {
            throw new BusinessException("CATALOG_CATEGORY_PARENT_INVALID", "类目不能将自身设为父类目");
        }
        int level = 1;
        if (parentId != 0L) {
            CatalogCategory parent = requireCategory(parentId);
            level = (parent.getLevelNo() == null ? 1 : parent.getLevelNo()) + 1;
            if (level > 3) {
                throw new BusinessException("CATALOG_CATEGORY_LEVEL_INVALID", "类目最多支持三级");
            }
        }
        CatalogCategory category = new CatalogCategory();
        category.setId(request.id());
        category.setParentId(parentId);
        category.setCategoryName(request.categoryName().trim());
        category.setCategoryCode(request.categoryCode().trim().toUpperCase());
        category.setLevelNo(level);
        category.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        category.setStatus(request.status());
        category.setIconUrl(trimToNull(request.iconUrl()));
        category.setRemark(trimToNull(request.remark()));
        try {
            if (category.getId() == null) {
                catalogCategoryMapper.insert(category);
            } else {
                CatalogCategory existing = requireCategory(category.getId());
                category.setVersion(existing.getVersion());
                if (catalogCategoryMapper.updateById(category) == 0) {
                    throw new BusinessException("CATALOG_CATEGORY_CONCURRENT", "类目已被其他操作修改，请刷新后重试");
                }
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("CATALOG_CATEGORY_CODE_EXISTS", "类目编码已存在");
        }
        return category.getId();
    }

    /**
     * 修改类目启停状态。
     *
     * @param id 类目ID
     * @param status 目标状态
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("CATALOG_CATEGORY_STATUS_INVALID", "类目状态必须为 0 或 1");
        }
        CatalogCategory category = requireCategory(id);
        category.setStatus(status);
        if (catalogCategoryMapper.updateById(category) == 0) {
            throw new BusinessException("CATALOG_CATEGORY_CONCURRENT", "类目已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 逻辑删除类目并校验子类目和商品引用。
     *
     * @param id 类目ID
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void delete(Long id) {
        requireCategory(id);
        long children = catalogCategoryMapper.selectCount(new LambdaQueryWrapper<CatalogCategory>()
                .eq(CatalogCategory::getParentId, id));
        if (children > 0) {
            throw new BusinessException("CATALOG_CATEGORY_HAS_CHILDREN", "请先删除或移动子类目");
        }
        long products = catalogProductMapper.selectCount(new LambdaQueryWrapper<CatalogProduct>()
                .eq(CatalogProduct::getCategoryId, id));
        if (products > 0) {
            throw new BusinessException("CATALOG_CATEGORY_IN_USE", "该类目下仍有商品，不能删除");
        }
        catalogCategoryMapper.deleteById(id);
    }

    /**
     * 查询类目，不存在时抛出统一业务异常。
     *
     * @param id 类目ID
     * @return 类目实体
     * @author Henfon
     * @date 2026-08-30
     */
    private CatalogCategory requireCategory(Long id) {
        if (id == null) {
            throw new BusinessException("CATALOG_CATEGORY_NOT_FOUND", "类目不存在");
        }
        CatalogCategory category = catalogCategoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException("CATALOG_CATEGORY_NOT_FOUND", "类目不存在");
        }
        return category;
    }

    /**
     * 清理可选文本。
     *
     * @param value 原始文本
     * @return 清理后的文本或空值
     * @author Henfon
     * @date 2026-08-30
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
