package com.henfon.shop.export.service;

import com.henfon.shop.export.entity.ExportTask;
import com.henfon.shop.export.mapper.ExportTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 导出任务兜底调度。
 *
 * <p>承担三种异常场景，保证任务在任何部署形态下都不会永久卡住：</p>
 * <ol>
 *   <li><b>消息未到达</b>：开发环境默认关闭 RocketMQ 消费监听器，或 broker 抖动导致消息丢失，
 *       排队中的任务需要被主动捡起执行；</li>
 *   <li><b>僵尸任务</b>：进程在生成过程中重启，任务停留在 RUNNING，需要回收后重新排队；</li>
 *   <li><b>文件过期</b>：成功任务的下载时效到期后，需要清理对象存储中的文件并置为已过期。</li>
 * </ol>
 *
 * <p>所有回收动作都是幂等的：执行入口靠状态条件更新认领任务，多实例并发扫描不会重复生成，
 * 与消息消费同时触发也只会有一个执行者真正产出文件。</p>
 *
 * @author Henfon
 * @date 2026-09-16
 */
@Component
public class ExportTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExportTaskScheduler.class);

    private final ExportTaskMapper exportTaskMapper;

    private final ExportTaskExecutor exportTaskExecutor;

    private final ExportTaskService exportTaskService;

    /** 排队多久仍未开始执行即判定为消息未送达。 */
    private final long pendingDelayMs;

    /** 认领后多久仍未完成即判定为僵尸任务。 */
    private final long runningTimeoutMs;

    /** 单次扫描每类任务的处理上限，避免异常积压时长时间占用调度线程。 */
    private final int batchSize;

    /**
     * 创建导出任务兜底调度。
     *
     * @param exportTaskMapper 导出任务数据访问对象
     * @param exportTaskExecutor 导出任务执行器
     * @param exportTaskService 导出任务服务，用于清理过期文件
     * @param pendingDelayMs 排队判定为消息丢失的等待时长
     * @param runningTimeoutMs 认领判定为僵尸任务的超时时长
     * @param batchSize 单次扫描处理上限
     * @author Henfon
     * @date 2026-09-16
     */
    public ExportTaskScheduler(ExportTaskMapper exportTaskMapper,
                               ExportTaskExecutor exportTaskExecutor,
                               ExportTaskService exportTaskService,
                               @Value("${shop.export.pending-delay-ms:60000}") long pendingDelayMs,
                               @Value("${shop.export.running-timeout-ms:1800000}") long runningTimeoutMs,
                               @Value("${shop.export.scan-batch-size:20}") int batchSize) {
        this.exportTaskMapper = exportTaskMapper;
        this.exportTaskExecutor = exportTaskExecutor;
        this.exportTaskService = exportTaskService;
        this.pendingDelayMs = Math.max(pendingDelayMs, 5000L);
        this.runningTimeoutMs = Math.max(runningTimeoutMs, 60000L);
        this.batchSize = Math.max(batchSize, 1);
    }

    /**
     * 扫描并恢复未能正常执行的任务，同时清理到期的导出文件。
     *
     * @author Henfon
     * @date 2026-09-16
     */
    @Scheduled(fixedDelayString = "${shop.export.scan-ms:60000}",
            initialDelayString = "${shop.export.scan-initial-delay-ms:20000}")
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        int requeued = rescueStalePending(now);
        int rescued = rescueStaleRunning(now);
        int expired = expireFinishedFiles(now);
        if (requeued > 0 || rescued > 0 || expired > 0) {
            log.info("导出任务兜底扫描完成，补投排队任务={}，回收僵尸任务={}，清理过期文件={}",
                    requeued, rescued, expired);
        }
    }

    /**
     * 补投长时间排队未执行的任务。
     *
     * @param now 当前时间
     * @return 补投数量
     * @author Henfon
     * @date 2026-09-16
     */
    private int rescueStalePending(LocalDateTime now) {
        LocalDateTime deadline = now.minus(pendingDelayMs, ChronoUnit.MILLIS);
        List<ExportTask> tasks = exportTaskMapper.findStalePending(deadline, batchSize);
        int count = 0;
        for (ExportTask task : tasks) {
            log.info("导出任务{}排队超时未执行，由兜底扫描接管", task.getId());
            exportTaskExecutor.execute(task.getId());
            count++;
        }
        return count;
    }

    /**
     * 回收进程重启后残留的僵尸任务并立即重跑。
     *
     * @param now 当前时间
     * @return 回收数量
     * @author Henfon
     * @date 2026-09-16
     */
    private int rescueStaleRunning(LocalDateTime now) {
        LocalDateTime deadline = now.minus(runningTimeoutMs, ChronoUnit.MILLIS);
        List<ExportTask> tasks = exportTaskMapper.findStaleRunning(deadline, batchSize);
        int count = 0;
        for (ExportTask task : tasks) {
            if (exportTaskMapper.rescueStaleRunning(task.getId()) != 1) {
                // 任务恰好在本次扫描中被正常完成，无需回收。
                continue;
            }
            log.warn("导出任务{}认领后长时间未完成，已回收并重新排队", task.getId());
            exportTaskExecutor.execute(task.getId());
            count++;
        }
        return count;
    }

    /**
     * 清理已过期的导出文件。
     *
     * @param now 当前时间
     * @return 清理数量
     * @author Henfon
     * @date 2026-09-16
     */
    private int expireFinishedFiles(LocalDateTime now) {
        List<ExportTask> tasks = exportTaskMapper.findExpired(now, batchSize);
        int count = 0;
        for (ExportTask task : tasks) {
            exportTaskService.deleteObjectQuietly(task);
            exportTaskMapper.markExpired(task.getId());
            count++;
        }
        return count;
    }
}
