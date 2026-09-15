package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 管理端系统配置保存请求。
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
public record SystemConfigSaveRequest(
        @NotBlank(message = "店铺名称不能为空") String storeName,
        @NotBlank(message = "客服电话不能为空") String storeContactPhone,
        @NotBlank(message = "客服邮箱不能为空") @Email(message = "客服邮箱格式不正确") String storeContactEmail,
        @Min(value = 0, message = "库存预警阈值不能小于0") Integer lowStockThreshold,
        Boolean autoNotifyEmail,
        Boolean autoTrackingSync,
        Boolean enableWechatPay,
        Integer version) {
}
