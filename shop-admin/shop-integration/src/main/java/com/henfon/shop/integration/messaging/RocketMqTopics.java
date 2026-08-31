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
    public static final String REFUND_SUCCEEDED = "shop.refund.succeeded";
    public static final String AFTER_SALE_CREATED = "shop.after-sale.created";
    public static final String AFTER_SALE_APPROVED = "shop.after-sale.approved";
    public static final String AFTER_SALE_REJECTED = "shop.after-sale.rejected";
    public static final String AFTER_SALE_CANCELLED = "shop.after-sale.cancelled";
    public static final String AFTER_SALE_COMPLETED = "shop.after-sale.completed";
    public static final String PRODUCT_CHANGED = "shop.product.changed";

    private RocketMqTopics() {
        // 工具常量类不允许实例化。
    }
}
