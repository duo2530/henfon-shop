package com.henfon.shop.integration.logistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 快递100轨迹查询适配器。
 *
 * <p>该适配器只负责调用外部接口和转换模型，不直接写交易数据库，便于未来替换为快递鸟或菜鸟物流。</p>
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Component
public class Kuaidi100LogisticsProvider implements LogisticsProvider {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, String> CARRIER_CODES = Map.ofEntries(
            Map.entry("顺丰", "shunfeng"),
            Map.entry("顺丰速运", "shunfeng"),
            Map.entry("圆通", "yuantong"),
            Map.entry("圆通速递", "yuantong"),
            Map.entry("中通", "zhongtong"),
            Map.entry("中通快递", "zhongtong"),
            Map.entry("申通", "shentong"),
            Map.entry("申通快递", "shentong"),
            Map.entry("韵达", "yunda"),
            Map.entry("韵达快递", "yunda"),
            Map.entry("极兔", "jtexpress"),
            Map.entry("极兔速递", "jtexpress"),
            Map.entry("京东", "jd"),
            Map.entry("京东物流", "jd"),
            Map.entry("邮政", "youzhengguonei"),
            Map.entry("中国邮政", "youzhengguonei")
    );

    private final Kuaidi100Properties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    /**
     * 创建快递100物流服务商。
     *
     * @param properties 快递100配置
     * @param objectMapper JSON转换器
     * @author Henfon
     * @date 2026-09-01
     */
    public Kuaidi100LogisticsProvider(Kuaidi100Properties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(properties.getConnectTimeoutMs(), 500));
        requestFactory.setReadTimeout(Math.max(properties.getReadTimeoutMs(), 1000));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * 返回快递100服务商编码。
     *
     * @return kuaidi100
     * @author Henfon
     * @date 2026-09-01
     */
    @Override
    public String providerCode() {
        return "kuaidi100";
    }

    /**
     * 判断快递100是否具备调用条件。
     *
     * @return 配置启用且客户号、密钥均不为空时返回 true
     * @author Henfon
     * @date 2026-09-01
     */
    @Override
    public boolean enabled() {
        return properties.isEnabled()
                && StringUtils.hasText(properties.getCustomer())
                && StringUtils.hasText(properties.getKey());
    }

    /**
     * 查询快递100物流轨迹。
     *
     * @param logisticsCompany 物流公司名称或快递100编码
     * @param trackingNo 运单号
     * @return 标准化轨迹结果
     * @author Henfon
     * @date 2026-09-01
     */
    @Override
    public LogisticsTrackResult query(String logisticsCompany, String trackingNo) {
        String safeTrackingNo = trackingNo == null ? "" : trackingNo.trim();
        if (!enabled()) {
            return LogisticsTrackResult.failure(providerCode(), safeTrackingNo, "快递100未启用或缺少客户号、密钥配置");
        }
        if (!StringUtils.hasText(logisticsCompany) || !StringUtils.hasText(safeTrackingNo)) {
            return LogisticsTrackResult.failure(providerCode(), safeTrackingNo, "物流公司和运单号不能为空");
        }

        String carrierCode = resolveCarrierCode(logisticsCompany);
        if (!StringUtils.hasText(carrierCode)) {
            return LogisticsTrackResult.failure(providerCode(), safeTrackingNo,
                    "暂不支持自动识别物流公司，请填写快递100公司编码");
        }

        Map<String, Object> param = new LinkedHashMap<>();
        param.put("com", carrierCode);
        param.put("num", safeTrackingNo);
        String paramJson;
        try {
            paramJson = objectMapper.writeValueAsString(param);
        } catch (JsonProcessingException exception) {
            return LogisticsTrackResult.failure(providerCode(), safeTrackingNo, "物流查询参数生成失败");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("customer", properties.getCustomer().trim());
        form.add("param", paramJson);
        form.add("sign", md5(paramJson + properties.getKey().trim() + properties.getCustomer().trim()));
        form.add("schema", "json");
        try {
            String response = restClient.post()
                    .uri(properties.getQueryUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return convertResponse(logisticsCompany.trim(), carrierCode, safeTrackingNo, response);
        } catch (RestClientException exception) {
            // 外部服务不可用时返回可展示的失败结果，避免阻塞后台订单查询。
            return LogisticsTrackResult.failure(providerCode(), safeTrackingNo, "物流服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 将快递100原始响应转换为统一轨迹结果。
     *
     * @param carrierName 物流公司名称
     * @param carrierCode 物流公司编码
     * @param trackingNo 运单号
     * @param response 原始响应
     * @return 标准化结果
     * @author Henfon
     * @date 2026-09-01
     */
    private LogisticsTrackResult convertResponse(String carrierName, String carrierCode,
                                                  String trackingNo, String response) {
        if (!StringUtils.hasText(response)) {
            return LogisticsTrackResult.failure(providerCode(), trackingNo, "物流服务返回空响应");
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            String apiStatus = text(root, "status");
            String message = StringUtils.hasText(text(root, "message")) ? text(root, "message") : "查询成功";
            if (StringUtils.hasText(apiStatus) && !"200".equals(apiStatus)) {
                return LogisticsTrackResult.failure(providerCode(), trackingNo, message);
            }

            List<LogisticsTrackNode> nodes = new ArrayList<>();
            JsonNode data = root.path("data");
            if (data.isArray()) {
                data.forEach(item -> {
                    LocalDateTime eventTime = parseTime(text(item, "time"));
                    if (eventTime != null && StringUtils.hasText(text(item, "context"))) {
                        nodes.add(new LogisticsTrackNode(eventTime, mapState(text(root, "state")),
                                text(item, "context"), text(item, "location")));
                    }
                });
            }
            // 快递100通常按最新节点在前返回，统一改为时间正序，方便现有门户时间线展示。
            Collections.reverse(nodes);
            String status = mapState(text(root, "state"));
            return new LogisticsTrackResult(true, providerCode(), carrierCode, carrierName,
                    trackingNo, status, nodes.isEmpty() ? "暂无物流轨迹" : message, nodes);
        } catch (JsonProcessingException exception) {
            return LogisticsTrackResult.failure(providerCode(), trackingNo, "物流服务响应格式无法识别");
        }
    }

    /**
     * 将物流公司名称解析为快递100编码。
     *
     * @param logisticsCompany 物流公司名称或编码
     * @return 快递100编码
     * @author Henfon
     * @date 2026-09-01
     */
    private String resolveCarrierCode(String logisticsCompany) {
        String normalized = logisticsCompany.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("[a-z0-9-]{2,32}")) {
            return normalized;
        }
        return CARRIER_CODES.get(logisticsCompany.trim());
    }

    /**
     * 读取 JSON 文本节点。
     *
     * @param node JSON节点
     * @param field 字段名
     * @return 文本值或空字符串
     * @author Henfon
     * @date 2026-09-01
     */
    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    /**
     * 解析第三方时间文本。
     *
     * @param value 时间文本
     * @return 本地时间，无法解析时返回 null
     * @author Henfon
     * @date 2026-09-01
     */
    private LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    /**
     * 将快递100状态转换为系统状态。
     *
     * @param state 快递100状态码
     * @return 系统物流状态
     * @author Henfon
     * @date 2026-09-01
     */
    private String mapState(String state) {
        return switch (state) {
            case "1" -> "PICKED_UP";
            case "3" -> "SIGNED";
            case "5" -> "DELIVERING";
            case "2", "4", "6" -> "EXCEPTION";
            default -> "IN_TRANSIT";
        };
    }

    /**
     * 计算快递100要求的 MD5 签名。
     *
     * @param value 待签名文本
     * @return 小写十六进制摘要
     * @author Henfon
     * @date 2026-09-01
     */
    private String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 未提供 MD5 算法", exception);
        }
    }
}
