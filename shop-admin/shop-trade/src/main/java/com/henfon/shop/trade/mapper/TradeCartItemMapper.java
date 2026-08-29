package com.henfon.shop.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.trade.entity.TradeCartItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购物车明细数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Mapper
public interface TradeCartItemMapper extends BaseMapper<TradeCartItem> {
}
