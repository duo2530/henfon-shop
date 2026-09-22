package com.henfon.shop.ai.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 人工接待空闲回收定时任务。
 *
 * @author Henfon
 * @date 2026-09-22
 */
@Component
public class AiAgentIdleScheduler {

    private final AiAgentIdleService idleService;

    /**
     * 创建空闲回收定时任务。
     *
     * @param idleService 空闲收敛服务
     * @author Henfon
     * @date 2026-09-22
     */
    public AiAgentIdleScheduler(AiAgentIdleService idleService) {
        this.idleService = idleService;
    }

    /**
     * 定时扫描人工接待中的会话。
     *
     * 扫描间隔与阈值是独立的两个量：间隔只决定"多久发现一次超时"，阈值决定"多久算超时"。
     * 间隔取 1 分钟，让 30 分钟的阈值实际落在 30~31 分钟之间，偏差可接受。
     *
     * @author Henfon
     * @date 2026-09-22
     */
    @Scheduled(fixedDelayString = "${shop.ai.agent.idle-scan-ms:60000}",
            initialDelayString = "${shop.ai.agent.idle-initial-delay-ms:60000}")
    public void sweepIdleSessions() {
        idleService.sweep();
    }
}
