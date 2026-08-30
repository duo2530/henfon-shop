package com.henfon.shop.trade.service;

import com.henfon.shop.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 订单状态机单元测试。
 *
 * @author Henfon
 * @date 2026-08-30
 */
class TradeOrderStateMachineTest {

    /**
     * 校验正常履约状态转换均可通过。
     *
     * @author Henfon
     * @date 2026-08-30
     */
    @Test
    void shouldAllowNormalFulfillmentTransitions() {
        assertDoesNotThrow(() -> TradeOrderStateMachine.requireTransition(
                TradeOrderStateMachine.STATUS_PENDING_PAYMENT,
                TradeOrderStateMachine.STATUS_PENDING_SHIPMENT));
        assertDoesNotThrow(() -> TradeOrderStateMachine.requireTransition(
                TradeOrderStateMachine.STATUS_PENDING_SHIPMENT,
                TradeOrderStateMachine.STATUS_SHIPPED));
        assertDoesNotThrow(() -> TradeOrderStateMachine.requireTransition(
                TradeOrderStateMachine.STATUS_SHIPPED,
                TradeOrderStateMachine.STATUS_COMPLETED));
    }

    /**
     * 校验已完成订单不能回退到待付款。
     *
     * @author Henfon
     * @date 2026-08-30
     */
    @Test
    void shouldRejectIllegalBackwardTransition() {
        assertFalse(TradeOrderStateMachine.canTransition(
                TradeOrderStateMachine.STATUS_COMPLETED,
                TradeOrderStateMachine.STATUS_PENDING_PAYMENT));
        assertThrows(BusinessException.class, () -> TradeOrderStateMachine.requireTransition(
                TradeOrderStateMachine.STATUS_COMPLETED,
                TradeOrderStateMachine.STATUS_PENDING_PAYMENT));
    }

    /**
     * 校验已取消订单不能再次进入退款或履约流程。
     *
     * @author Henfon
     * @date 2026-08-30
     */
    @Test
    void shouldRejectTransitionFromCancelled() {
        assertFalse(TradeOrderStateMachine.canTransition(
                TradeOrderStateMachine.STATUS_CANCELLED,
                TradeOrderStateMachine.STATUS_REFUNDING));
        assertFalse(TradeOrderStateMachine.canTransition(
                TradeOrderStateMachine.STATUS_CANCELLED,
                TradeOrderStateMachine.STATUS_SHIPPED));
    }
}
