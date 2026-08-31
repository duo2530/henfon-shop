package com.henfon.shop.content.mapper;

import com.henfon.shop.content.entity.TradeEventOutboxNotificationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 交易 Outbox 通知事件读取接口。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Mapper
public interface TradeEventOutboxNotificationMapper {

    /**
     * 查询待转换为会员通知的交易事件。
     *
     * @param limit 本次最多读取数量
     * @return 交易事件列表
     * @author Henfon
     * @date 2026-08-31
     */
    @Select("SELECT id, event_id, event_type, payload FROM trade_event_outbox "
            + "WHERE is_deleted = 0 AND status = 1 AND event_type IN "
            + "('PAYMENT_SUCCEEDED', 'ORDER_SHIPPED', 'REFUND_SUCCEEDED', "
            + "'AFTER_SALE_CREATED', 'AFTER_SALE_APPROVED', 'AFTER_SALE_REJECTED', "
            + "'AFTER_SALE_CANCELLED', 'AFTER_SALE_COMPLETED') "
            + "ORDER BY id DESC LIMIT #{limit}")
    List<TradeEventOutboxNotificationRecord> findNotificationEvents(@Param("limit") int limit);
}
