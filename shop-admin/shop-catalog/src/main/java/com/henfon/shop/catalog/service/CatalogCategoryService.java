package com.henfon.shop.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.catalog.entity.CatalogCategory;
import com.henfon.shop.catalog.dto.CatalogCategorySaveRequest;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.mapper.CatalogCategoryMapper;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.integration.storage.MinioStorageService;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private final MinioStorageService minioStorageService;

    /**
     * 创建商品类目服务。
     *
     * @param catalogCategoryMapper 类目数据访问对象
     * @param catalogProductMapper 商品数据访问对象
     * @param minioStorageService MinIO 文件服务
     * @author Henfon
     * @date 2026-08-29
     */
    public CatalogCategoryService(CatalogCategoryMapper catalogCategoryMapper, CatalogProductMapper catalogProductMapper,
                                  MinioStorageService minioStorageService) {
        this.catalogCategoryMapper = catalogCategoryMapper;
        this.catalogProductMapper = catalogProductMapper;
        this.minioStorageService = minioStorageService;
    }

    /**
     * 查询启用类目列表。
     *
     * <p>上级类目被停用后，其整棵子树同样不再下发：门户拿到的是一张平表、由前端拼树，
     * 父节点缺失的子节点会被顶成一级类目。库里状态不改写，上级重新启用后下级自动恢复。</p>
     *
     * @return 类目列表
     * @author Henfon
     * @date 2026-08-29
     */
    public List<CatalogCategory> listEnabled() {
        // 商品编辑页只展示启用类目，停用类目仍保留在后台数据库中。
        List<CatalogCategory> all = catalogCategoryMapper.selectList(new LambdaQueryWrapper<CatalogCategory>()
                .orderByAsc(CatalogCategory::getParentId)
                .orderByAsc(CatalogCategory::getSortNo));
        Set<Long> hidden = collectDisabledSubtreeIds(all);
        return withAccessibleIcon(all.stream()
                .filter(category -> category.getStatus() != null && category.getStatus() == 1)
                .filter(category -> !hidden.contains(category.getId()))
                .toList());
    }

    /**
     * 汇总「自身停用或任一祖先停用」的类目 ID。
     *
     * @param all 全部未删除类目
     * @return 不应在门户与管理端选择器里出现的类目 ID
     * @author Henfon
     * @date 2026-09-18
     */
    private Set<Long> collectDisabledSubtreeIds(List<CatalogCategory> all) {
        Set<Long> hidden = new HashSet<>();
        for (CatalogCategory category : all) {
            if (category.getStatus() == null || category.getStatus() != 1) {
                hidden.add(category.getId());
            }
        }
        boolean expanded = true;
        while (expanded) {
            expanded = false;
            for (CatalogCategory category : all) {
                Long parentId = category.getParentId();
                if (parentId != null && parentId != 0L && hidden.contains(parentId) && hidden.add(category.getId())) {
                    expanded = true;
                }
            }
        }
        return hidden;
    }

    /**
     * 查询后台类目树数据。
     *
     * @return 全部未删除类目
     * @author Henfon
     * @date 2026-08-30
     */
    public List<CatalogCategory> listAdmin() {
        return withAccessibleIcon(catalogCategoryMapper.selectList(new LambdaQueryWrapper<CatalogCategory>()
                .orderByAsc(CatalogCategory::getParentId)
                .orderByAsc(CatalogCategory::getSortNo)
                .orderByAsc(CatalogCategory::getId)));
    }

    /**
     * 展开类目及其全部下级类目 ID。
     *
     * <p>商品挂在类目树的叶子节点上，按上级类目筛选商品时必须一并带上下级，否则只会命中直接挂在该类目下的商品。
     * 类目总量在百级以内，一次性取出后逐层收敛比递归查询更简单。</p>
     *
     * @param rootId 根类目ID
     * @return 根类目及其全部后代的 ID，根类目为空时返回空集合
     * @author Henfon
     * @date 2026-09-18
     */
    public List<Long> subtreeCategoryIds(Long rootId) {
        if (rootId == null) {
            return List.of();
        }
        List<CatalogCategory> all = catalogCategoryMapper.selectList(new LambdaQueryWrapper<CatalogCategory>()
                .select(CatalogCategory::getId, CatalogCategory::getParentId));
        Set<Long> scope = new LinkedHashSet<>();
        scope.add(rootId);
        boolean expanded = true;
        while (expanded) {
            expanded = false;
            for (CatalogCategory category : all) {
                if (category.getParentId() != null && scope.contains(category.getParentId())
                        && scope.add(category.getId())) {
                    expanded = true;
                }
            }
        }
        return new ArrayList<>(scope);
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
        category.setIconUrl(minioStorageService.normalizeReference(trimToNull(request.iconUrl())));
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
     * 把类目图标的稳定引用换算成本次请求可访问的地址。
     *
     * <p>库内存的是 MinIO 对象键或外部图片地址：对象键前端无法直接展示，历史遗留的预签名地址又会在 24 小时后失效，
     * 因此读取时统一重签。外部地址与对象存储不可用的情况由 {@link MinioStorageService#resolveAccessUrl} 保留原值。</p>
     *
     * @param categories 类目列表
     * @return 同一批类目
     * @author Henfon
     * @date 2026-09-18
     */
    private List<CatalogCategory> withAccessibleIcon(List<CatalogCategory> categories) {
        categories.forEach(category ->
                category.setIconUrl(minioStorageService.resolveAccessUrl(category.getIconUrl())));
        return categories;
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
