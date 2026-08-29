package com.henfon.shop.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.catalog.entity.CatalogProductFeature;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品卖点数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Mapper
public interface CatalogProductFeatureMapper extends BaseMapper<CatalogProductFeature> {
}
