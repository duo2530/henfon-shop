package com.henfon.shop.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.catalog.entity.CatalogSku;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品 SKU 数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Mapper
public interface CatalogSkuMapper extends BaseMapper<CatalogSku> {
}
