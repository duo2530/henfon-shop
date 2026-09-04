package com.henfon.shop.integration.messaging;

/**
 * RocketMQ 领域事件主题常量。
 *
 * @author Henfon
 * @date 2026-08-29
 */
public final class RocketMqTopics {

    public static final String ORDER_CREATED = "shop_order_created";
    public static final String ORDER_CANCELLED = "shop_order_cancelled";
    public static final String ORDER_CLOSED_TIMEOUT = "shop_order_closed_timeout";
    public static final String ORDER_COMPLETED = "shop_order_completed";
    public static final String PAYMENT_SUCCEEDED = "shop_payment_succeeded";
    public static final String INVENTORY_RESERVED = "shop_inventory_reserved";
    public static final String INVENTORY_RELEASED = "shop_inventory_released";
    public static final String ORDER_SHIPPED = "shop_order_shipped";
    public static final String ORDER_AUDIT_APPROVED = "shop_order_audit_approved";
    public static final String ORDER_AUDIT_REJECTED = "shop_order_audit_rejected";
    public static final String REFUND_APPROVED = "shop_refund_approved";
    public static final String REFUND_SUCCEEDED = "shop_refund_succeeded";
    public static final String PARTIAL_REFUND_SUCCEEDED = "shop_partial_refund_succeeded";
    public static final String AFTER_SALE_CREATED = "shop_after_sale_created";
    public static final String AFTER_SALE_APPROVED = "shop_after_sale_approved";
    public static final String AFTER_SALE_RETURN_RECEIVED = "shop_after_sale_return_received";
    public static final String AFTER_SALE_REJECTED = "shop_after_sale_rejected";
    public static final String AFTER_SALE_CANCELLED = "shop_after_sale_cancelled";
    public static final String AFTER_SALE_COMPLETED = "shop_after_sale_completed";
    public static final String PRODUCT_CHANGED = "shop_product_changed";

    private RocketMqTopics() {
        // 工具常量类不允许实例化。
    }
}
