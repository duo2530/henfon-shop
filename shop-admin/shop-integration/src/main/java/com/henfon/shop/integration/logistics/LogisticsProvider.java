package com.henfon.shop.integration.logistics;

/**
 * 物流服务商统一接口。
 *
 * @author Henfon
 * @date 2026-09-01
 */
public interface LogisticsProvider {

    /**
     * 返回服务商编码。
     *
     * @return 服务商编码
     * @author Henfon
     * @date 2026-09-01
     */
    String providerCode();

    /**
     * 判断当前服务商是否已启用并完成必要配置。
     *
     * @return true 表示可以调用外部服务
     * @author Henfon
     * @date 2026-09-01
     */
    boolean enabled();

    /**
     * 查询运单轨迹并转换为系统统一模型。
     *
     * @param logisticsCompany 物流公司名称或服务商编码
     * @param trackingNo 运单号
     * @return 轨迹查询结果
     * @author Henfon
     * @date 2026-09-01
     */
    LogisticsTrackResult query(String logisticsCompany, String trackingNo);
}
