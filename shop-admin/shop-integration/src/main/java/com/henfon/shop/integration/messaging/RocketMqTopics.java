package com.henfon.shop.integration.messaging;

/**
 * RocketMQ 领域事件主题常量。
 *
 * @author Henfon
 * @date 2026-08-29
 */
public final class RocketMqTopics {

    public static final String ORDER_CREATED = "shop.order.created";
    public static final String ORDER_CANCELLED = "shop.order.cancelled";
    public static final String ORDER_CLOSED_TIMEOUT = "shop.order.closed.timeout";
    public static final String ORDER_COMPLETED = "shop.order.completed";
    public static final String PAYMENT_SUCCEEDED = "shop.payment.succeeded";
    public static final String INVENTORY_RESERVED = "shop.inventory.reserved";
    public static final String INVENTORY_RELEASED = "shop.inventory.released";
    public static final String ORDER_SHIPPED = "shop.order.shipped";
    public static final String REFUND_APPROVED = "shop.refund.approved";
    public static final String PRODUCT_CHANGED = "shop.product.changed";

    private RocketMqTopics() {
        // 工具常量类不允许实例化。
    }
}
