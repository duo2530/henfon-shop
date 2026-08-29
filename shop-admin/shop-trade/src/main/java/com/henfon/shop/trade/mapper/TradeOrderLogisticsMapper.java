package com.henfon.shop.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.trade.entity.TradeOrderLogistics;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单物流轨迹数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Mapper
public interface TradeOrderLogisticsMapper extends BaseMapper<TradeOrderLogistics> {
}
