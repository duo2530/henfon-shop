package com.henfon.shop.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.dto.SystemConfigResponse;
import com.henfon.shop.identity.dto.SystemConfigSaveRequest;
import com.henfon.shop.identity.entity.SysConfig;
import com.henfon.shop.identity.mapper.SysConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统配置应用服务单元测试。
 *
 * @author Henfon
 * @date 2026-09-15
 */
@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock
    private SysConfigMapper sysConfigMapper;

    private SystemConfigService systemConfigService;

    /**
     * 初始化被测系统配置服务。
     *
     * @author Henfon
     * @date 2026-09-15
     */
    @BeforeEach
    void setUp() {
        systemConfigService = new SystemConfigService(sysConfigMapper, new ObjectMapper());
    }

    /**
     * 验证首次查询会创建服务端默认配置。
     *
     * @author Henfon
     * @date 2026-09-15
     */
    @Test
    void shouldCreateDefaultConfigWhenTenantConfigDoesNotExist() {
        when(sysConfigMapper.selectOne(any())).thenReturn(null);
        when(sysConfigMapper.insert(any(SysConfig.class))).thenAnswer(invocation -> {
            SysConfig config = invocation.getArgument(0);
            config.setId(1L);
            config.setVersion(0);
            return 1;
        });

        SystemConfigResponse response = systemConfigService.get(9L);

        assertEquals("Henfon商城", response.storeName());
        assertEquals(10, response.lowStockThreshold());
        verify(sysConfigMapper).insert(any(SysConfig.class));
    }

    /**
     * 验证配置版本不一致时拒绝覆盖其他操作的新值。
     *
     * @author Henfon
     * @date 2026-09-15
     */
    @Test
    void shouldRejectSaveWhenVersionConflicts() {
        SysConfig config = existingConfig(3L, 5);
        when(sysConfigMapper.selectOne(any())).thenReturn(config);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> systemConfigService.save(0L, saveRequest(4)));

        assertEquals("CONFIG_VERSION_CONFLICT", exception.getCode());
    }

    /**
     * 验证配置保存会写入请求值并携带当前版本。
     *
     * @author Henfon
     * @date 2026-09-15
     */
    @Test
    void shouldSaveConfigValuesWhenVersionMatches() {
        SysConfig config = existingConfig(3L, 5);
        when(sysConfigMapper.selectOne(any())).thenReturn(config);
        when(sysConfigMapper.updateById(config)).thenReturn(1);

        SystemConfigResponse response = systemConfigService.save(0L, saveRequest(5));

        assertEquals("测试店铺", response.storeName());
        assertEquals(20, response.lowStockThreshold());
        ArgumentCaptor<SysConfig> captor = ArgumentCaptor.forClass(SysConfig.class);
        verify(sysConfigMapper).updateById(captor.capture());
        // 断言持久化 JSON 包含请求中的关键配置，避免只验证内存响应。
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getConfigJson().contains("测试店铺"));
    }

    /**
     * 验证数据库未更新时返回并发冲突，避免前端误判保存成功。
     *
     * @author Henfon
     * @date 2026-09-15
     */
    @Test
    void shouldRejectSaveWhenDatabaseUpdateFails() {
        SysConfig config = existingConfig(3L, 5);
        when(sysConfigMapper.selectOne(any())).thenReturn(config);
        when(sysConfigMapper.updateById(config)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> systemConfigService.save(0L, saveRequest(5)));

        assertEquals("CONFIG_VERSION_CONFLICT", exception.getCode());
    }

    /**
     * 构造测试用的已有配置实体。
     *
     * @param id 配置ID
     * @param version 配置版本
     * @return 配置实体
     * @author Henfon
     * @date 2026-09-15
     */
    private SysConfig existingConfig(Long id, Integer version) {
        SysConfig config = new SysConfig();
        config.setId(id);
        config.setTenantId(0L);
        config.setVersion(version);
        config.setConfigJson("{\"storeName\":\"旧店铺\",\"storeContactPhone\":\"4000000000\",\"storeContactEmail\":\"old@example.com\",\"lowStockThreshold\":10,\"autoNotifyEmail\":true,\"autoTrackingSync\":true,\"enableWechatPay\":true}");
        return config;
    }

    /**
     * 构造测试用的系统配置保存请求。
     *
     * @param version 配置版本
     * @return 保存请求
     * @author Henfon
     * @date 2026-09-15
     */
    private SystemConfigSaveRequest saveRequest(Integer version) {
        return new SystemConfigSaveRequest("测试店铺", "13800000000", "test@example.com",
                20, true, false, true, version);
    }
}
