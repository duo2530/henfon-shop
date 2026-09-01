package com.henfon.shop.reporting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.integration.messaging.DomainEvent;
import com.henfon.shop.reporting.entity.ReportingEventProjection;
import com.henfon.shop.reporting.mapper.ReportingEventProjectionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 报表领域事件投影服务。
 *
 * <p>事件先以原始载荷落库，后续报表聚合可以按事件发生时间重放；
 * 唯一事件编号由数据库约束保证重复投递幂等。</p>
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
public class ReportingEventProjectionService {

    private static final Logger log = LoggerFactory.getLogger(ReportingEventProjectionService.class);

    private final ReportingEventProjectionMapper projectionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建报表事件投影服务。
     *
     * @param projectionMapper 事件投影数据访问对象
     * @param objectMapper JSON序列化器
     * @author Henfon
     * @date 2026-09-01
     */
    public ReportingEventProjectionService(ReportingEventProjectionMapper projectionMapper,
                                            ObjectMapper objectMapper) {
        this.projectionMapper = projectionMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存订单、支付或退款领域事件投影。
     *
     * @param topic RocketMQ主题
     * @param event 领域事件
     * @author Henfon
     * @date 2026-09-01
     */
    @Transactional
    public void project(String topic, DomainEvent event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()) {
            throw new IllegalArgumentException("报表事件缺少eventId");
        }
        ReportingEventProjection projection = new ReportingEventProjection();
        projection.setEventId(event.eventId());
        projection.setEventType(requiredText(event.eventType(), "eventType"));
        projection.setTopic(requiredText(topic, "topic"));
        projection.setAggregateId(event.aggregateId());
        projection.setOccurredAt(event.occurredAt() == null ? LocalDateTime.now()
                : LocalDateTime.ofInstant(event.occurredAt(), ZoneId.systemDefault()));
        projection.setPayload(toJson(event.payload()));
        int inserted = projectionMapper.insertIgnore(projection);
        if (inserted == 0) {
            // 重复消息属于正常重试场景，记录调试日志后确认消费成功。
            log.debug("报表事件已投影，忽略重复消息，eventId={}", event.eventId());
        }
    }

    /**
     * 将事件载荷序列化为合法JSON文本。
     *
     * @param payload 原始事件载荷
     * @return JSON文本
     * @author Henfon
     * @date 2026-09-01
     */
    private String toJson(Object payload) {
        try {
            if (payload == null) {
                return "null";
            }
            if (payload instanceof String text) {
                objectMapper.readTree(text);
                return text;
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalArgumentException("报表事件载荷格式错误", exception);
        }
    }

    /**
     * 校验事件文本字段。
     *
     * @param value 字段值
     * @param field 字段名
     * @return 去除首尾空白后的字段值
     * @author Henfon
     * @date 2026-09-01
     */
    private String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("报表事件缺少" + field);
        }
        return value.trim();
    }
}
