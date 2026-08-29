package com.henfon.shop.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.catalog.entity.CatalogCategory;
import com.henfon.shop.catalog.mapper.CatalogCategoryMapper;
import org.springframework.stereotype.Service;

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

    /**
     * 创建商品类目服务。
     *
     * @param catalogCategoryMapper 类目数据访问对象
     * @author Henfon
     * @date 2026-08-29
     */
    public CatalogCategoryService(CatalogCategoryMapper catalogCategoryMapper) {
        this.catalogCategoryMapper = catalogCategoryMapper;
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
}
