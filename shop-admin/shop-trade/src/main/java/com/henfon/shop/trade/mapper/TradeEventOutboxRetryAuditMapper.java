package com.henfon.shop.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.trade.entity.TradeEventOutboxRetryAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * Outbox 人工补偿审计数据访问接口。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@Mapper
public interface TradeEventOutboxRetryAuditMapper extends BaseMapper<TradeEventOutboxRetryAudit> {
}
