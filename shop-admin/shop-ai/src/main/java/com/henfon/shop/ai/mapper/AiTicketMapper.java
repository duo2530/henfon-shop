package com.henfon.shop.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.ai.entity.AiTicket;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 客服转人工工单 Mapper。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Mapper
public interface AiTicketMapper extends BaseMapper<AiTicket> {
}
