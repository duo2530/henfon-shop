package com.henfon.shop.integration.logistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 快递100物流适配器测试。
 *
 * @author Henfon
 * @date 2026-09-01
 */
class Kuaidi100LogisticsProviderTest {

    /**
     * 未配置密钥时不允许调用外部物流接口。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldStayDisabledWithoutCredentials() {
        Kuaidi100Properties properties = new Kuaidi100Properties();
        properties.setEnabled(true);
        Kuaidi100LogisticsProvider provider = new Kuaidi100LogisticsProvider(properties, new ObjectMapper());

        assertFalse(provider.enabled());
        assertFalse(provider.query("顺丰速运", "SF123456").success());
    }

    /**
     * 配置开关、客户号和密钥齐全时服务商可用。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldEnableWhenCredentialsArePresent() {
        Kuaidi100Properties properties = new Kuaidi100Properties();
        properties.setEnabled(true);
        properties.setCustomer("customer");
        properties.setKey("key");
        Kuaidi100LogisticsProvider provider = new Kuaidi100LogisticsProvider(properties, new ObjectMapper());

        assertTrue(provider.enabled());
    }
}
