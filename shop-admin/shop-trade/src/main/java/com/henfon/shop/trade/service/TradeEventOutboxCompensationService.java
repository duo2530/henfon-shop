package com.henfon.shop.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.trade.entity.TradeEventOutbox;
import com.henfon.shop.trade.entity.TradeEventOutboxRetryAudit;
import com.henfon.shop.trade.mapper.TradeEventOutboxMapper;
import com.henfon.shop.trade.mapper.TradeEventOutboxRetryAuditMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final TradeEventOutboxRetryAuditMapper retryAuditMapper;

    /**
     * 创建 Outbox 死信补偿服务。
     *
     * @param outboxMapper Outbox 数据访问对象
     * @author Henfon
     * @date 2026-08-31
     */
    public TradeEventOutboxCompensationService(TradeEventOutboxMapper outboxMapper) {
        this(outboxMapper, null);
    }

    /**
     * 创建带审计记录能力的 Outbox 死信补偿服务。
     *
     * @param outboxMapper Outbox 数据访问对象
     * @param retryAuditMapper 人工补偿审计数据访问对象
     * @author Henfon
     * @date 2026-09-04
     */
    @Autowired
    public TradeEventOutboxCompensationService(TradeEventOutboxMapper outboxMapper,
                                                TradeEventOutboxRetryAuditMapper retryAuditMapper) {
        this.outboxMapper = outboxMapper;
        this.retryAuditMapper = retryAuditMapper;
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
        return retryDeadEvent(eventId, null);
    }

    /**
     * 将死信重新入队并记录人工补偿审计信息。
     *
     * @param eventId 事件唯一标识
     * @param operator 操作人用户名
     * @return 已重置的 Outbox 事件
     * @throws BusinessException 事件不存在、状态不为死信或并发更新失败
     * @author Henfon
     * @date 2026-09-04
     */
    @Transactional
    public TradeEventOutbox retryDeadEvent(String eventId, String operator) {
        String normalizedEventId = normalizeFilter(eventId);
        if (!StringUtils.hasText(normalizedEventId)) {
            recordRetryAudit(null, normalizedEventId, operator, "FAILED", "事件标识不能为空");
            throw new BusinessException("TRADE_OUTBOX_EVENT_ID_INVALID", "事件标识不能为空");
        }
        TradeEventOutbox event = outboxMapper.selectOne(new LambdaQueryWrapper<TradeEventOutbox>()
                .eq(TradeEventOutbox::getEventId, normalizedEventId));
        if (event == null) {
            recordRetryAudit(null, normalizedEventId, operator, "FAILED", "Outbox 事件不存在");
            throw new BusinessException("TRADE_OUTBOX_NOT_FOUND", "Outbox 事件不存在");
        }
        if (!Integer.valueOf(STATUS_DEAD).equals(event.getStatus())) {
            recordRetryAudit(event, normalizedEventId, operator, "FAILED", "仅死信事件允许人工重试");
            throw new BusinessException("TRADE_OUTBOX_STATUS_INVALID", "仅死信事件允许人工重试");
        }

        // 只重置投递状态，保留最近错误信息，便于运营复盘本次补偿前的失败原因。
        event.setStatus(STATUS_PENDING);
        event.setRetryCount(0);
        event.setNextRetryAt(LocalDateTime.now());
        event.setPublishedAt(null);
        // 将本次人工补偿操作者、时间和结果写回事件，便于审计追踪。
        event.setManualRetryBy(normalizeFilter(operator));
        event.setManualRetryAt(LocalDateTime.now());
        event.setManualRetryResult("SUCCESS");
        if (outboxMapper.updateById(event) != 1) {
            recordRetryAudit(event, normalizedEventId, operator, "FAILED", "Outbox 事件已被其他操作修改，请刷新后重试");
            throw new BusinessException("TRADE_OUTBOX_CONCURRENT_UPDATE", "Outbox 事件已被其他操作修改，请刷新后重试");
        }
        recordRetryAudit(event, normalizedEventId, operator, "SUCCESS", null);
        return event;
    }

    /**
     * 记录一次人工补偿操作，审计失败不影响原始补偿结果。
     *
     * @param event Outbox 事件
     * @param eventId 事件标识
     * @param operator 操作人
     * @param result 操作结果
     * @param errorMessage 失败原因
     * @author Henfon
     * @date 2026-09-04
     */
    private void recordRetryAudit(TradeEventOutbox event, String eventId, String operator,
                                  String result, String errorMessage) {
        if (retryAuditMapper == null) {
            return;
        }
        try {
            // 审计写入采用独立对象，避免后续事件状态更新覆盖历史操作记录。
            TradeEventOutboxRetryAudit audit = new TradeEventOutboxRetryAudit();
            audit.setEventId(event != null && event.getEventId() != null ? event.getEventId() : eventId);
            audit.setEventType(event == null ? null : event.getEventType());
            audit.setOperator(normalizeFilter(operator));
            audit.setResult(result);
            audit.setErrorMessage(errorMessage);
            retryAuditMapper.insert(audit);
        } catch (Exception exception) {
            // 审计表异常时记录日志即可，不能阻断运营补偿主流程。
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("写入 Outbox 人工补偿审计失败，eventId={}", eventId, exception);
        }
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
