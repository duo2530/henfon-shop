package com.henfon.shop.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.trade.entity.TradeEventOutbox;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交易领域事件 Outbox 数据访问接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Mapper
public interface TradeEventOutboxMapper extends BaseMapper<TradeEventOutbox> {
}
