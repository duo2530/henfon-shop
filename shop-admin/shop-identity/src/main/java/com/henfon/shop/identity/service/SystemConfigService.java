package com.henfon.shop.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.dto.SystemConfigResponse;
import com.henfon.shop.identity.dto.SystemConfigSaveRequest;
import com.henfon.shop.identity.entity.SysConfig;
import com.henfon.shop.identity.mapper.SysConfigMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 租户级系统配置应用服务。
 *
 * @author Henfon
 * @date 2026-09-15
 */
@Service
public class SystemConfigService {

    // 首次查询时写入的占位店铺信息，不含任何虚构品牌，运营可在「系统设置」页改为真实值。
    private static final String DEFAULT_STORE_NAME = "Henfon商城";
    private static final String DEFAULT_CONTACT_PHONE = "400-888-9999";
    private static final String DEFAULT_CONTACT_EMAIL = "support@henfon.com";
    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;

    private final SysConfigMapper sysConfigMapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建系统配置应用服务。
     *
     * @param sysConfigMapper 系统配置数据访问对象
     * @param objectMapper JSON 序列化对象
     * @author Henfon
     * @date 2026-09-15
     */
    public SystemConfigService(SysConfigMapper sysConfigMapper, ObjectMapper objectMapper) {
        this.sysConfigMapper = sysConfigMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询租户系统配置；首次查询时创建服务端默认配置。
     *
     * @param tenantId 租户ID
     * @return 系统配置
     * @author Henfon
     * @date 2026-09-15
     */
    @Transactional
    public SystemConfigResponse get(Long tenantId) {
        SysConfig config = findByTenant(tenantId);
        if (config == null) {
            config = createDefaultConfig(tenantId);
        }
        return toResponse(config);
    }

    /**
     * 保存租户系统配置，并使用版本号阻止并发覆盖。
     *
     * @param tenantId 租户ID
     * @param request 配置保存请求
     * @return 保存后的配置
     * @author Henfon
     * @date 2026-09-15
     */
    @Transactional
    public SystemConfigResponse save(Long tenantId, SystemConfigSaveRequest request) {
        validateRequest(request);
        SysConfig config = findByTenant(tenantId);
        if (config == null) {
            config = createDefaultConfig(tenantId);
        }
        if (request.version() != null && !request.version().equals(config.getVersion())) {
            throw new BusinessException("CONFIG_VERSION_CONFLICT", "系统配置已被其他操作修改，请刷新后重试");
        }
        config.setConfigJson(writeJson(toValues(request)));
        if (sysConfigMapper.updateById(config) <= 0) {
            throw new BusinessException("CONFIG_VERSION_CONFLICT", "系统配置保存失败，请刷新后重试");
        }
        return toResponse(config);
    }

    /**
     * 恢复租户系统配置为服务端默认值。
     *
     * @param tenantId 租户ID
     * @param version 当前配置版本
     * @return 重置后的配置
     * @author Henfon
     * @date 2026-09-15
     */
    @Transactional
    public SystemConfigResponse reset(Long tenantId, Integer version) {
        SysConfig config = findByTenant(tenantId);
        if (config == null) {
            config = createDefaultConfig(tenantId);
        }
        if (version != null && !version.equals(config.getVersion())) {
            throw new BusinessException("CONFIG_VERSION_CONFLICT", "系统配置已被其他操作修改，请刷新后重试");
        }
        config.setConfigJson(writeJson(defaultValues()));
        if (sysConfigMapper.updateById(config) <= 0) {
            throw new BusinessException("CONFIG_VERSION_CONFLICT", "系统配置重置失败，请刷新后重试");
        }
        return toResponse(config);
    }

    /**
     * 按租户查询配置记录。
     *
     * @param tenantId 租户ID
     * @return 配置记录
     * @author Henfon
     * @date 2026-09-15
     */
    private SysConfig findByTenant(Long tenantId) {
        return sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getTenantId, tenantId == null ? 0L : tenantId)
                .last("LIMIT 1"));
    }

    /**
     * 创建服务端默认配置。
     *
     * @param tenantId 租户ID
     * @return 新建配置记录
     * @author Henfon
     * @date 2026-09-15
     */
    private SysConfig createDefaultConfig(Long tenantId) {
        SysConfig config = new SysConfig();
        config.setTenantId(tenantId == null ? 0L : tenantId);
        config.setConfigJson(writeJson(defaultValues()));
        try {
            sysConfigMapper.insert(config);
            return config;
        } catch (DuplicateKeyException exception) {
            // 并发首次读取时由另一请求完成初始化，重新读取唯一配置记录。
            SysConfig existing = findByTenant(tenantId);
            if (existing != null) {
                return existing;
            }
            throw exception;
        }
    }

    /**
     * 构造服务端默认配置值。
     *
     * @return 默认配置键值
     * @author Henfon
     * @date 2026-09-15
     */
    private Map<String, Object> defaultValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("storeName", DEFAULT_STORE_NAME);
        values.put("storeContactPhone", DEFAULT_CONTACT_PHONE);
        values.put("storeContactEmail", DEFAULT_CONTACT_EMAIL);
        values.put("lowStockThreshold", DEFAULT_LOW_STOCK_THRESHOLD);
        values.put("autoNotifyEmail", true);
        values.put("autoTrackingSync", true);
        values.put("enableWechatPay", true);
        return values;
    }

    /**
     * 将保存请求转换为持久化键值。
     *
     * @param request 配置保存请求
     * @return 配置键值
     * @author Henfon
     * @date 2026-09-15
     */
    private Map<String, Object> toValues(SystemConfigSaveRequest request) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("storeName", request.storeName().trim());
        values.put("storeContactPhone", request.storeContactPhone().trim());
        values.put("storeContactEmail", request.storeContactEmail().trim());
        values.put("lowStockThreshold", request.lowStockThreshold());
        values.put("autoNotifyEmail", Boolean.TRUE.equals(request.autoNotifyEmail()));
        values.put("autoTrackingSync", Boolean.TRUE.equals(request.autoTrackingSync()));
        values.put("enableWechatPay", Boolean.TRUE.equals(request.enableWechatPay()));
        return values;
    }

    /**
     * 将配置实体转换为前端响应。
     *
     * @param config 配置实体
     * @return 配置响应
     * @author Henfon
     * @date 2026-09-15
     */
    private SystemConfigResponse toResponse(SysConfig config) {
        Map<String, Object> values = readJson(config.getConfigJson());
        return new SystemConfigResponse(
                stringValue(values, "storeName", DEFAULT_STORE_NAME),
                stringValue(values, "storeContactPhone", DEFAULT_CONTACT_PHONE),
                stringValue(values, "storeContactEmail", DEFAULT_CONTACT_EMAIL),
                intValue(values, "lowStockThreshold", DEFAULT_LOW_STOCK_THRESHOLD),
                booleanValue(values, "autoNotifyEmail", true),
                booleanValue(values, "autoTrackingSync", true),
                booleanValue(values, "enableWechatPay", true),
                config.getVersion());
    }

    /**
     * 校验系统配置请求。
     *
     * @param request 配置保存请求
     * @author Henfon
     * @date 2026-09-15
     */
    private void validateRequest(SystemConfigSaveRequest request) {
        if (request.lowStockThreshold() == null || request.lowStockThreshold() < 0) {
            throw new BusinessException("CONFIG_INVALID", "库存预警阈值不能小于0");
        }
        if (!StringUtils.hasText(request.storeName()) || !StringUtils.hasText(request.storeContactPhone())
                || !StringUtils.hasText(request.storeContactEmail())) {
            throw new BusinessException("CONFIG_INVALID", "店铺基础信息不能为空");
        }
    }

    /**
     * 读取 JSON 配置对象。
     *
     * @param json 配置 JSON
     * @return 配置键值
     * @author Henfon
     * @date 2026-09-15
     */
    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new BusinessException("CONFIG_CORRUPTED", "系统配置数据格式异常");
        }
    }

    /**
     * 写入 JSON 配置对象。
     *
     * @param values 配置键值
     * @return 配置 JSON
     * @author Henfon
     * @date 2026-09-15
     */
    private String writeJson(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("CONFIG_SERIALIZE_FAILED", "系统配置序列化失败");
        }
    }

    /**
     * 读取字符串配置值。
     *
     * @param values 配置键值
     * @param key 配置键
     * @param fallback 默认值
     * @return 字符串配置值
     * @author Henfon
     * @date 2026-09-15
     */
    private String stringValue(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    /**
     * 读取整数配置值。
     *
     * @param values 配置键值
     * @param key 配置键
     * @param fallback 默认值
     * @return 整数配置值
     * @author Henfon
     * @date 2026-09-15
     */
    private Integer intValue(Map<String, Object> values, String key, int fallback) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /**
     * 读取布尔配置值。
     *
     * @param values 配置键值
     * @param key 配置键
     * @param fallback 默认值
     * @return 布尔配置值
     * @author Henfon
     * @date 2026-09-15
     */
    private Boolean booleanValue(Map<String, Object> values, String key, boolean fallback) {
        Object value = values.get(key);
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
