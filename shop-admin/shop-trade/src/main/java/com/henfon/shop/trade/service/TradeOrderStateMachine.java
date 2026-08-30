package com.henfon.shop.trade.service;

import com.henfon.shop.common.exception.BusinessException;

import java.util.Map;
import java.util.Set;

/**
 * 订单状态机，统一维护订单状态编码及合法转换关系。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public final class TradeOrderStateMachine {

    public static final int STATUS_PENDING_PAYMENT = 10;
    public static final int STATUS_PENDING_SHIPMENT = 20;
    public static final int STATUS_SHIPPED = 30;
    public static final int STATUS_COMPLETED = 40;
    public static final int STATUS_CANCELLED = 50;
    public static final int STATUS_REFUNDING = 60;

    private static final Map<Integer, Set<Integer>> TRANSITIONS = Map.of(
            STATUS_PENDING_PAYMENT, Set.of(STATUS_PENDING_SHIPMENT, STATUS_CANCELLED),
            STATUS_PENDING_SHIPMENT, Set.of(STATUS_SHIPPED, STATUS_CANCELLED, STATUS_REFUNDING),
            STATUS_SHIPPED, Set.of(STATUS_COMPLETED, STATUS_REFUNDING),
            STATUS_COMPLETED, Set.of(STATUS_REFUNDING),
            STATUS_CANCELLED, Set.of(),
            STATUS_REFUNDING, Set.of()
    );

    private TradeOrderStateMachine() {
        // 状态机仅提供静态校验能力，不允许实例化。
    }

    /**
     * 判断订单状态是否允许转换。
     *
     * @param from 原状态
     * @param to 目标状态
     * @return 是否允许转换
     * @author Henfon
     * @date 2026-08-30
     */
    public static boolean canTransition(Integer from, int to) {
        return from != null && TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * 校验订单状态转换，不合法时抛出统一业务异常。
     *
     * @param from 原状态
     * @param to 目标状态
     * @author Henfon
     * @date 2026-08-30
     */
    public static void requireTransition(Integer from, int to) {
        if (!canTransition(from, to)) {
            throw new BusinessException("TRADE_ORDER_STATUS_INVALID",
                    "订单状态不允许从" + describe(from) + "转换为" + describe(to));
        }
    }

    /**
     * 获取订单状态中文描述。
     *
     * @param status 状态编码
     * @return 状态描述
     * @author Henfon
     * @date 2026-08-30
     */
    public static String describe(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case STATUS_PENDING_PAYMENT -> "待付款";
            case STATUS_PENDING_SHIPMENT -> "待发货";
            case STATUS_SHIPPED -> "已发货";
            case STATUS_COMPLETED -> "已完成";
            case STATUS_CANCELLED -> "已取消";
            case STATUS_REFUNDING -> "退款中";
            default -> "未知(" + status + ")";
        };
    }
}
