package com.henfon.shop.export.service;

import com.henfon.shop.integration.messaging.DomainEvent;
import com.henfon.shop.integration.messaging.RocketMqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 导出任务消息消费者。
 *
 * <p>消息只承担「唤醒执行」的职责，不承担唯一性：执行器会以状态条件更新认领任务，
 * 因此重复投递、兜底扫描并发触发同一任务时，仍然只会生成一次文件。</p>
 *
 * <p>注意开发环境默认关闭消费监听器（见 RocketMqListenerStartupConfiguration），
 * 该场景下由 ExportTaskScheduler 的兜底扫描接替执行，任务不会停留在排队状态。</p>
 *
 * @author Henfon
 * @date 2026-09-16
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.EXPORT_TASK_REQUESTED,
        consumerGroup = "${shop.rocketmq.consumer.export-task-group:shop-export-task}")
public class ExportTaskConsumer implements RocketMQListener<DomainEvent> {

    private static final Logger log = LoggerFactory.getLogger(ExportTaskConsumer.class);

    private final ExportTaskExecutor exportTaskExecutor;

    /**
     * 创建导出任务消费者。
     *
     * @param exportTaskExecutor 导出任务执行器
     * @author Henfon
     * @date 2026-09-16
     */
    public ExportTaskConsumer(ExportTaskExecutor exportTaskExecutor) {
        this.exportTaskExecutor = exportTaskExecutor;
    }

    /**
     * 消费任务就绪事件并触发文件生成。
     *
     * @param event 领域事件，载荷为任务ID
     * @author Henfon
     * @date 2026-09-16
     */
    @Override
    public void onMessage(DomainEvent event) {
        Long taskId = resolveTaskId(event);
        if (taskId == null) {
            // 载荷异常属于无法补救的消息，直接丢弃并留痕，避免进入死信反复重投。
            log.warn("导出任务消息载荷无法解析，已忽略：eventId={}，payload={}",
                    event == null ? null : event.eventId(), event == null ? null : event.payload());
            return;
        }
        // 执行器内部已消化全部异常并落库失败状态，这里抛异常只会触发无意义的重投。
        exportTaskExecutor.execute(taskId);
    }

    /**
     * 从事件载荷中解析任务ID。
     *
     * @param event 领域事件
     * @return 任务ID，无法解析时返回 null
     * @author Henfon
     * @date 2026-09-16
     */
    private Long resolveTaskId(DomainEvent event) {
        if (event == null || event.payload() == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(event.payload()));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
