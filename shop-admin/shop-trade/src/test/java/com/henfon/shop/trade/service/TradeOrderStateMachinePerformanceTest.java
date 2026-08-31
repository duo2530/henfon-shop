package com.henfon.shop.trade.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单状态机性能基线测试。
 *
 * <p>该测试用于在 CI 中捕获状态转换规则被意外改成线性扫描或远程调用的回归。
 * 它不是生产压测，真实接口吞吐量应使用阶段 7 压测脚本在独立环境执行。</p>
 *
 * @author Henfon
 * @date 2026-08-31
 */
class TradeOrderStateMachinePerformanceTest {

    /**
     * 验证状态机在大量转换校验下维持可接受延迟。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldValidateTransitionsWithinBaseline() {
        final int warmup = 5_000;
        final int operations = 100_000;
        for (int index = 0; index < warmup; index++) {
            TradeOrderStateMachine.canTransition(TradeOrderStateMachine.STATUS_PENDING_PAYMENT,
                    TradeOrderStateMachine.STATUS_PENDING_SHIPMENT);
        }
        long started = System.nanoTime();
        int allowed = 0;
        for (int index = 0; index < operations; index++) {
            // 混合合法与非法转换，避免 JIT 只优化单一分支。
            int target = index % 3 == 0
                    ? TradeOrderStateMachine.STATUS_PENDING_SHIPMENT
                    : TradeOrderStateMachine.STATUS_CANCELLED;
            if (TradeOrderStateMachine.canTransition(
                    TradeOrderStateMachine.STATUS_PENDING_PAYMENT, target)) {
                allowed++;
            }
        }
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        long maxMillis = Long.getLong("shop.performance.maxMillis", 5_000L);
        assertTrue(allowed > 0, "性能基线未执行有效转换");
        assertTrue(elapsedMillis <= maxMillis,
                () -> "状态机性能超过基线: " + elapsedMillis + "ms > " + maxMillis + "ms");
    }
}
