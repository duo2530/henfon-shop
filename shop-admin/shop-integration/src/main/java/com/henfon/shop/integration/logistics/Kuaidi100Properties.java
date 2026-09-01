package com.henfon.shop.integration.logistics;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 快递100物流查询配置。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@ConfigurationProperties(prefix = "shop.integration.logistics.kuaidi100")
public class Kuaidi100Properties {

    private boolean enabled;
    private String customer;
    private String key;
    private String queryUrl = "https://poll.kuaidi100.com/poll/query.do";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 10000;

    /**
     * 读取服务商启用开关。
     *
     * @return 是否启用
     * @author Henfon
     * @date 2026-09-01
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置服务商启用开关。
     *
     * @param enabled 是否启用
     * @author Henfon
     * @date 2026-09-01
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 读取快递100客户号。
     *
     * @return 客户号
     * @author Henfon
     * @date 2026-09-01
     */
    public String getCustomer() {
        return customer;
    }

    /**
     * 设置快递100客户号。
     *
     * @param customer 客户号
     * @author Henfon
     * @date 2026-09-01
     */
    public void setCustomer(String customer) {
        this.customer = customer;
    }

    /**
     * 读取快递100密钥。
     *
     * @return 服务密钥
     * @author Henfon
     * @date 2026-09-01
     */
    public String getKey() {
        return key;
    }

    /**
     * 设置快递100密钥。
     *
     * @param key 服务密钥
     * @author Henfon
     * @date 2026-09-01
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * 读取轨迹查询地址。
     *
     * @return 查询地址
     * @author Henfon
     * @date 2026-09-01
     */
    public String getQueryUrl() {
        return queryUrl;
    }

    /**
     * 设置轨迹查询地址。
     *
     * @param queryUrl 查询地址
     * @author Henfon
     * @date 2026-09-01
     */
    public void setQueryUrl(String queryUrl) {
        this.queryUrl = queryUrl;
    }

    /**
     * 读取连接超时时间。
     *
     * @return 毫秒数
     * @author Henfon
     * @date 2026-09-01
     */
    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    /**
     * 设置连接超时时间。
     *
     * @param connectTimeoutMs 毫秒数
     * @author Henfon
     * @date 2026-09-01
     */
    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    /**
     * 读取响应超时时间。
     *
     * @return 毫秒数
     * @author Henfon
     * @date 2026-09-01
     */
    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    /**
     * 设置响应超时时间。
     *
     * @param readTimeoutMs 毫秒数
     * @author Henfon
     * @date 2026-09-01
     */
    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
