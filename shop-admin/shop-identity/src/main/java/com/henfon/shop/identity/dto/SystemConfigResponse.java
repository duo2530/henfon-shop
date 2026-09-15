package com.henfon.shop.identity.dto;

/**
 * 管理端系统配置响应。
 *
 * @param storeName 店铺名称
 * @param storeContactPhone 客服电话
 * @param storeContactEmail 客服邮箱
 * @param lowStockThreshold 库存预警阈值
 * @param autoNotifyEmail 是否自动发送邮件通知
 * @param autoTrackingSync 是否自动同步物流轨迹
 * @param enableWechatPay 是否启用微信支付
 * @param version 配置版本
 * @author Henfon
 * @date 2026-09-15
 */
public record SystemConfigResponse(
        String storeName,
        String storeContactPhone,
        String storeContactEmail,
        Integer lowStockThreshold,
        Boolean autoNotifyEmail,
        Boolean autoTrackingSync,
        Boolean enableWechatPay,
        Integer version) {
}
