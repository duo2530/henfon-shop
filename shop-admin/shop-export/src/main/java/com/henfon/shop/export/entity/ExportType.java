package com.henfon.shop.export.entity;

import java.util.Locale;

/**
 * 导出类型。
 *
 * <p>枚举名同时是接口与数据库中的类型标识；展示名用于任务列表，工作表名用于 Excel 页签，
 * 权限编码用于按数据类型隔离导出能力，避免仅拥有查询权限即可批量下载全量数据。</p>
 *
 * @author Henfon
 * @date 2026-09-16
 */
public enum ExportType {

    /** 商品库数据。 */
    PRODUCT("商品库数据导出", "商品库", "catalog:product:export"),

    /** 全渠道订单明细。 */
    ORDER("全渠道订单明细", "订单明细", "trade:order:export"),

    /** 会员用户资产。 */
    MEMBER("会员用户资产报表", "会员名单", "member:user:export"),

    /** 营销优惠券报表。 */
    COUPON("营销优惠券报表", "优惠券", "marketing:coupon:export"),

    /** 财务资金对账单。 */
    FINANCE("财务资金对账单", "资金对账", "payment:transaction:export"),

    /** 商品动销排行。 */
    PRODUCT_RANKING("商品动销排行", "商品动销", "reporting:product:export"),

    /** 管理员登录日志。 */
    LOGIN_LOG("管理员登录日志", "登录日志", "system:audit:login:export"),

    /** 系统操作审计日志。 */
    OPERATION_LOG("系统操作审计日志", "操作日志", "system:audit:operation:export"),

    /** 库存流水明细。 */
    STOCK_LOG("库存流水明细", "库存流水", "inventory:stock:export"),

    /** 单个秒杀活动的商品明细。 */
    FLASH_SALE_ITEM("秒杀活动商品明细", "秒杀商品", "marketing:flash:export"),

    /** 单个秒杀活动的库存预占记录。 */
    FLASH_SALE_RESERVATION("秒杀活动预占记录", "秒杀预占", "marketing:flash:export");

    private final String displayName;

    private final String sheetName;

    private final String permission;

    ExportType(String displayName, String sheetName, String permission) {
        this.displayName = displayName;
        this.sheetName = sheetName;
        this.permission = permission;
    }

    /**
     * 导出展示名称。
     *
     * @return 展示名称
     * @author Henfon
     * @date 2026-09-16
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Excel 工作表名称。
     *
     * @return 工作表名称
     * @author Henfon
     * @date 2026-09-16
     */
    public String sheetName() {
        return sheetName;
    }

    /**
     * 该类型数据所需的导出权限编码。
     *
     * @return 权限编码
     * @author Henfon
     * @date 2026-09-16
     */
    public String permission() {
        return permission;
    }

    /**
     * 解析导出类型，非法值返回 null 由调用方转换为业务异常。
     *
     * @param value 类型文本
     * @return 导出类型
     * @author Henfon
     * @date 2026-09-16
     */
    public static ExportType parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (ExportType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
