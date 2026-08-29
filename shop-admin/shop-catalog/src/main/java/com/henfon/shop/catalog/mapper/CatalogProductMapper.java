package com.henfon.shop.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.catalog.entity.CatalogProduct;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品目录数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Mapper
public interface CatalogProductMapper extends BaseMapper<CatalogProduct> {
}
