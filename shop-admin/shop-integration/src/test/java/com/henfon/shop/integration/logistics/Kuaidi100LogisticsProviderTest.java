package com.henfon.shop.integration.logistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /**
     * 签名必须为 32 位大写，否则快递100 返回 503「验证签名失败」。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldSubmitUppercaseSignatureAndParseTracksInAscendingOrder() throws IOException {
        String payload = "{\"message\":\"ok\",\"status\":\"200\",\"state\":\"3\",\"data\":["
                + "{\"time\":\"2026-09-02 10:00:00\",\"context\":\"已签收\",\"location\":\"深圳\"},"
                + "{\"time\":\"2026-09-01 09:00:00\",\"context\":\"已揽收\",\"location\":\"广州\"}]}";
        Probe probe = new Probe(payload);

        LogisticsTrackResult result = probe.provider().query("顺丰速运", "SF1234567890");

        assertTrue(result.success());
        assertEquals("SIGNED", result.status());
        assertEquals(2, result.nodes().size());
        // 快递100 最新节点在前，适配器统一改成时间正序，门户时间线才不会倒着显示。
        assertEquals("已揽收", result.nodes().get(0).description());
        assertEquals("已签收", result.nodes().get(1).description());

        String param = "{\"com\":\"shunfeng\",\"num\":\"SF1234567890\"}";
        String expected = md5Hex(param + "key" + "customer").toUpperCase();
        assertEquals(expected, probe.capturedSign().get());
    }

    /**
     * 失败响应没有 status 字段，必须靠 result / returnCode 识别，不能静默当成「成功且暂无轨迹」。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldReportFailurePayloadWithoutStatusField() throws IOException {
        Probe probe = new Probe("{\"result\":false,\"returnCode\":\"503\",\"message\":\"验证签名失败\"}");

        LogisticsTrackResult result = probe.provider().query("中通快递", "ZT899920232023");

        assertFalse(result.success());
        assertEquals("验证签名失败", result.message());
    }

    /**
     * 查询无结果同样是失败响应，消息需原样透出给后台提示。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldReportNoResultMessage() throws IOException {
        Probe probe = new Probe("{\"result\":false,\"returnCode\":\"500\",\"message\":\"查询无结果，请隔段时间再查\"}");

        LogisticsTrackResult result = probe.provider().query("圆通速递", "SF81947688504");

        assertFalse(result.success());
        assertEquals("查询无结果，请隔段时间再查", result.message());
    }

    /**
     * 接口成功但没有节点时仍是成功结果，交由调用方按「暂无轨迹」重试。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldKeepEmptySuccessResultForRetry() throws IOException {
        Probe probe = new Probe("{\"message\":\"ok\",\"status\":\"200\",\"data\":[]}");

        LogisticsTrackResult result = probe.provider().query("顺丰速运", "SF1234567890");

        assertTrue(result.success());
        assertTrue(result.nodes().isEmpty());
    }

    /** 用本地 HTTP 服务替代快递100，捕获请求签名并回放指定响应。 */
    private static final class Probe {

        private final Kuaidi100LogisticsProvider provider;
        private final HttpServer server;
        private final AtomicReference<String> capturedSign = new AtomicReference<>("");

        Probe(String responseBody) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/poll/query.do", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                capturedSign.set(formField(body, "sign"));
                byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, payload.length);
                exchange.getResponseBody().write(payload);
                exchange.close();
            });
            server.start();
            Kuaidi100Properties properties = new Kuaidi100Properties();
            properties.setEnabled(true);
            properties.setCustomer("customer");
            properties.setKey("key");
            properties.setQueryUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/poll/query.do");
            this.provider = new Kuaidi100LogisticsProvider(properties, new ObjectMapper());
            this.server = server;
        }

        Kuaidi100LogisticsProvider provider() {
            return provider;
        }

        AtomicReference<String> capturedSign() {
            return capturedSign;
        }

    }

    /**
     * 读取表单字段。
     *
     * @param body 表单原文
     * @param name 字段名
     * @return 解码后的字段值
     * @author Henfon
     * @date 2026-09-18
     */
    private static String formField(String body, String name) {
        for (String pair : body.split("&")) {
            int index = pair.indexOf('=');
            if (index > 0 && name.equals(pair.substring(0, index))) {
                return URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    /**
     * 计算小写 MD5 摘要，供测试推导期望签名。
     *
     * @param value 待签名文本
     * @return 小写十六进制摘要
     * @author Henfon
     * @date 2026-09-18
     */
    private static String md5Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 未提供 MD5 算法", exception);
        }
    }
}
