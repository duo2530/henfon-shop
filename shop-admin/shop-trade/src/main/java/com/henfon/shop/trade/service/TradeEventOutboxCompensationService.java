package com.henfon.shop.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.trade.entity.TradeEventOutbox;
import com.henfon.shop.trade.mapper.TradeEventOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 交易事件 Outbox 死信查询与人工补偿服务。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class TradeEventOutboxCompensationService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_DEAD = 2;

    private final TradeEventOutboxMapper outboxMapper;

    /**
     * 创建 Outbox 死信补偿服务。
     *
     * @param outboxMapper Outbox 数据访问对象
     * @author Henfon
     * @date 2026-08-31
     */
    public TradeEventOutboxCompensationService(TradeEventOutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    /**
     * 分页查询待人工处理的死信事件。
     *
     * @param eventType 事件类型
     * @param topic RocketMQ 主题
     * @param aggregateId 业务聚合 ID
     * @param current 当前页
     * @param size 页大小
     * @return 死信事件分页结果
     * @author Henfon
     * @date 2026-08-31
     */
    public IPage<TradeEventOutbox> pageDeadEvents(String eventType, String topic, String aggregateId,
                                                   long current, long size) {
        // 死信查询固定过滤状态，避免把正常重试中的事件误展示为人工补偿对象。
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        // 运营筛选条件统一去除首尾空格，避免复制粘贴参数导致查询结果为空。
        String normalizedEventType = normalizeFilter(eventType);
        String normalizedTopic = normalizeFilter(topic);
        String normalizedAggregateId = normalizeFilter(aggregateId);
        LambdaQueryWrapper<TradeEventOutbox> wrapper = new LambdaQueryWrapper<TradeEventOutbox>()
                .eq(TradeEventOutbox::getStatus, STATUS_DEAD)
                .eq(StringUtils.hasText(normalizedEventType), TradeEventOutbox::getEventType, normalizedEventType)
                .eq(StringUtils.hasText(normalizedTopic), TradeEventOutbox::getTopic, normalizedTopic)
                .eq(StringUtils.hasText(normalizedAggregateId), TradeEventOutbox::getAggregateId, normalizedAggregateId)
                .orderByDesc(TradeEventOutbox::getUpdatedAt)
                .orderByDesc(TradeEventOutbox::getId);
        return outboxMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
    }

    /**
     * 将指定死信事件重新放回 Outbox 待发送队列。
     *
     * @param eventId 事件唯一标识
     * @return 已重置的 Outbox 事件
     * @throws BusinessException 事件不存在、状态不为死信或并发更新失败
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public TradeEventOutbox retryDeadEvent(String eventId) {
        String normalizedEventId = normalizeFilter(eventId);
        if (!StringUtils.hasText(normalizedEventId)) {
            throw new BusinessException("TRADE_OUTBOX_EVENT_ID_INVALID", "事件标识不能为空");
        }
        TradeEventOutbox event = outboxMapper.selectOne(new LambdaQueryWrapper<TradeEventOutbox>()
                .eq(TradeEventOutbox::getEventId, normalizedEventId));
        if (event == null) {
            throw new BusinessException("TRADE_OUTBOX_NOT_FOUND", "Outbox 事件不存在");
        }
        if (!Integer.valueOf(STATUS_DEAD).equals(event.getStatus())) {
            throw new BusinessException("TRADE_OUTBOX_STATUS_INVALID", "仅死信事件允许人工重试");
        }

        // 只重置投递状态，保留最近错误信息，便于运营复盘本次补偿前的失败原因。
        event.setStatus(STATUS_PENDING);
        event.setRetryCount(0);
        event.setNextRetryAt(LocalDateTime.now());
        event.setPublishedAt(null);
        if (outboxMapper.updateById(event) != 1) {
            throw new BusinessException("TRADE_OUTBOX_CONCURRENT_UPDATE", "Outbox 事件已被其他操作修改，请刷新后重试");
        }
        return event;
    }

    /**
     * 规范化 Outbox 运维筛选参数。
     *
     * @param value 原始参数
     * @return 去除首尾空格后的参数，空白参数返回 null
     * @author Henfon
     * @date 2026-09-04
     */
    private String normalizeFilter(String value) {
        // 将空白值转换为 null，便于 MyBatis-Plus 条件安全跳过。
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
