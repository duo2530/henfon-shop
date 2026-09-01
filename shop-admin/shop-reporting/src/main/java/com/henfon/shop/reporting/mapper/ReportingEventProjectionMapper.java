package com.henfon.shop.reporting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.henfon.shop.reporting.entity.ReportingEventProjection;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 报表领域事件投影数据访问接口。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Mapper
public interface ReportingEventProjectionMapper extends BaseMapper<ReportingEventProjection> {

    /**
     * 插入事件投影并忽略重复事件，保证 RocketMQ 重试不会生成重复记录。
     *
     * @param projection 事件投影记录
     * @return 实际插入行数，重复事件返回0
     * @author Henfon
     * @date 2026-09-01
     */
    @Insert("INSERT INTO reporting_event_projection "
            + "(event_id, event_type, topic, aggregate_id, occurred_at, payload, projected_at) "
            + "VALUES (#{projection.eventId}, #{projection.eventType}, #{projection.topic}, "
            + "#{projection.aggregateId}, #{projection.occurredAt}, "
            + "CAST(#{projection.payload} AS JSON), CURRENT_TIMESTAMP(3)) "
            + "ON DUPLICATE KEY UPDATE event_id = event_id")
    int insertIgnore(@Param("projection") ReportingEventProjection projection);
}
